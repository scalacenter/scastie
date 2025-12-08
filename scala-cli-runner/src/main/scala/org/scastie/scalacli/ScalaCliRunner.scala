package org.scastie.scalacli

import org.scastie.api._
import org.scastie.runtime.api._
import RuntimeCodecs._
import org.scastie.instrumentation.{InstrumentedInputs, InstrumentationFailureReport}
import com.typesafe.scalalogging.Logger
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.CompletableFuture
import java.io.{InputStream, OutputStream}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import org.scastie.instrumentation.InstrumentationFailure
import org.scastie.instrumentation.Instrument
import scala.util.control.NonFatal
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.TimeoutException
import scala.collection.concurrent.TrieMap
import org.scastie.buildinfo.BuildInfo
import scala.concurrent.duration.FiniteDuration
import io.circe._
import io.circe.parser._
import scala.collection.mutable.ListBuffer
import akka.pattern.after
import java.lang
import java.util.concurrent.atomic.AtomicReference
import cats.syntax.all._
import scala.jdk.FutureConverters._
import org.scastie.instrumentation.PositionMapper


sealed trait ScalaCliError {
  val msg: String
}

sealed trait BuildError extends ScalaCliError
sealed trait ScastieRuntimeError extends ScalaCliError

case class InvalidScalaVersion(version: String) extends BuildError {
  val msg = s"Invalid Scala version: $version"
}
case class InstrumentationFailure(failure: InstrumentationFailureReport) extends BuildError {
  val msg = s"Instrumentation failure: $failure"
}

case class CompilationError(diagnostics: List[Problem]) extends BuildError {
  val msg = ""
}

case class InternalBspError(msg: String) extends BuildError
case class InternalRuntimeError(msg: String) extends ScastieRuntimeError

case class RuntimeTimeout(msg: String) extends ScastieRuntimeError
case class BspTaskTimeout(msg: String) extends BuildError

case class RunOutput(
  instrumentation: List[Instrumentation],
  diagnostics: List[Problem],
  runtimeError: Option[org.scastie.runtime.api.RuntimeError],
  exitCode: Int
)

class ScalaCliRunner(coloredStackTrace: Boolean, workingDir: Path, compilationTimeout: FiniteDuration, reloadTimeout: FiniteDuration) {

  private val log = Logger("ScalaCliRunner")
  private var bspClient = new BspClient(coloredStackTrace, workingDir, compilationTimeout, reloadTimeout)
  private val scalaMain = workingDir.resolve("Main.scala")
  Files.createDirectories(scalaMain.getParent())

  def runTask(snippetId: SnippetId, inputs: ScalaCliInputs, timeout: FiniteDuration, onOutput: ProcessOutput => Any): Future[Either[ScalaCliError, RunOutput]] = {
    log.info(s"Running task with snippetId=$snippetId")
    log.trace(s"[${snippetId.base64UUID} - runTask] Starting runTask")
    build(snippetId, inputs).flatMap {
      case Right((value, positionMapper)) =>
        log.trace(s"[${snippetId.base64UUID} - runTask] Build successful, running forked")
        runForked(value, inputs.isWorksheetMode, onOutput, positionMapper)
      case Left(value) =>
        log.trace(s"[${snippetId.base64UUID} - runTask] Build failed: ${value.msg}")
        Future.successful(Left[ScalaCliError, RunOutput](value))
    }
  }

  def build(
    snippetId: SnippetId,
    inputs: BaseInputs,
  ): Future[Either[ScalaCliError, (BspClient.BuildOutput, Option[PositionMapper])]] = {

    log.trace(s"[${snippetId} - build] Starting Scala CLI run")

    val (instrumentedInput, positionMapper) = InstrumentedInputs(inputs) match {
      case Right(value) => (value.inputs, value.positionMapper)
      case Left(value) =>
        log.error(s"Error while instrumenting: $value")
        (inputs, None)
    }

    Files.write(scalaMain, instrumentedInput.code.getBytes)
    log.trace(s"[${snippetId.base64UUID} - build] Calling bspClient.build")

    bspClient.build(snippetId.base64UUID, inputs.isWorksheetMode, inputs.target, positionMapper).value.recover {
      case timeout: TimeoutException =>
        log.trace(s"[${snippetId.base64UUID} - build] BSP timeout")
        BspTaskTimeout("Build Server Timeout Exception").asLeft
      case err =>
        log.trace(s"[${snippetId.base64UUID} - build] BSP error: ${err.getMessage}")
        InternalBspError(err.getMessage).asLeft
    }
    .map {
        case Right(buildOutput) =>
          log.trace(s"[${snippetId.base64UUID} - build] Build successful, diagnostics: ${buildOutput.diagnostics.size}")
          Right((buildOutput, positionMapper))
        case Left(CompilationError(diagnostics)) =>
          log.trace(s"[${snippetId.base64UUID} - build] CompilationError with ${diagnostics.size} diagnostics")
          if (diagnostics.isEmpty) {
            log.warn(s"[${snippetId.base64UUID} - build] CompilationError with EMPTY diagnostics!")
          }
          Left(CompilationError(diagnostics))
        case Left(other) =>
          log.trace(s"[${snippetId.base64UUID} - build] Other error: ${other.msg}")
          Left(other)
      }
  }

  def runForked(
    bspRun: BspClient.BuildOutput,
    isWorksheet: Boolean,
    onOutput: ProcessOutput => Any,
    positionMapper: Option[PositionMapper] = None
  ): Future[Either[ScastieRuntimeError, RunOutput]] = {
    log.trace(s"[runForked] Starting forked execution, diagnostics: ${bspRun.diagnostics.size}")

    val outputBuffer: ListBuffer[String] = ListBuffer()
    val instrumentations: AtomicReference[List[Instrumentation]] = new AtomicReference(List())
    val runtimeError: AtomicReference[Option[org.scastie.runtime.api.RuntimeError]] = new AtomicReference(None)

    def forwardAndStorePrint(str: String, tpe: ProcessOutputType) = {
      val maybeRuntimeError = decode[org.scastie.runtime.api.RuntimeError](str)
      val maybeInstrumentation = decode[List[Instrumentation]](str)

      maybeRuntimeError.foreach { error =>
        val mappedLine = positionMapper match {
          case Some(mapper) => error.line.map(mapper.mapLine)
          case None => error.line
        }
        runtimeError.set(Some(error.copy(line = mappedLine)))
      }
      maybeInstrumentation.foreach(instrumentations.set(_))

      if (maybeRuntimeError.isLeft && maybeInstrumentation.isLeft) {
        outputBuffer.append(str)
        onOutput(ProcessOutput(line = str, tpe = tpe, id = None))
      }
    }

    val runProcess = bspRun.process.run(ProcessLogger.apply(
      (fout: String) => forwardAndStorePrint(fout, ProcessOutputType.StdOut),
      (ferr: String) => forwardAndStorePrint(ferr, ProcessOutputType.StdErr)
    ))

    val processResult = CompletableFuture.supplyAsync { () =>
      log.trace(s"[runForked] Waiting for process exitValue")
      val code = runProcess.exitValue()
      log.trace(s"[runForked] Process exited with code: $code")
      code
    }.orTimeout(10, TimeUnit.SECONDS).asScala
    processResult.onComplete(_ => runProcess.destroy())
    processResult.map { exitCode =>
      log.trace(s"[runForked] Creating RunOutput with exitCode=$exitCode, instrumentations=${instrumentations.get.size}")
      Right(RunOutput(instrumentations.get, bspRun.diagnostics, runtimeError.get, exitCode))
    }.recover {
      case _: TimeoutException =>
        log.trace(s"[runForked] Process timeout!")
        forwardAndStorePrint("Timeout exceeded.", ProcessOutputType.StdErr)
        Left(RuntimeTimeout("Timeout exceeded."))
      case err =>
        log.trace(s"[runForked] Process error: $err")
        forwardAndStorePrint(s"Unknown exception $err", ProcessOutputType.StdErr)
        Left(InternalRuntimeError(s"Unknown exception $err"))
    }
  }

  def restart(): Unit = {
    bspClient.end()
    bspClient = new BspClient(coloredStackTrace, workingDir, compilationTimeout, reloadTimeout)
  }

  def end(): Unit =
    bspClient.end()
}
