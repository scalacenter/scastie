package org.scastie.scalacli

import java.io.{InputStream, OutputStream}
import java.lang
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.FutureConverters._
import scala.sys.process._
import scala.util.control.NonFatal
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scastie.api._
import org.scastie.buildinfo.BuildInfo
import org.scastie.instrumentation.{InstrumentationFailureReport, InstrumentedInputs}
import org.scastie.instrumentation.Instrument
import org.scastie.instrumentation.InstrumentationFailure
import org.scastie.runtime.api._

import akka.pattern.after
import cats.syntax.all._
import com.typesafe.scalalogging.Logger
import io.circe._
import io.circe.parser._
import RuntimeCodecs._

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

class ScalaCliRunner(
    coloredStackTrace: Boolean,
    workingDir: Path,
    compilationTimeout: FiniteDuration,
    reloadTimeout: FiniteDuration
) {

  private val log = Logger("ScalaCliRunner")
  private var bspClient = new BspClient(coloredStackTrace, workingDir, compilationTimeout, reloadTimeout)
  private val scalaMain = workingDir.resolve("Main.scala")
  Files.createDirectories(scalaMain.getParent())

  def runTask(
    snippetId: SnippetId,
    inputs: ScalaCliInputs,
    timeout: FiniteDuration,
    onOutput: ProcessOutput => Any
  ): Future[Either[ScalaCliError, RunOutput]] = {
    log.info(s"Running task with snippetId=$snippetId")
    build(snippetId, inputs).flatMap {
      case Right((value, lineMapping)) => runForked(value, inputs.isWorksheetMode, onOutput, lineMapping)
      case Left(value)                 => Future.successful(Left[ScalaCliError, RunOutput](value))
    }
  }

  def build(
    snippetId: SnippetId,
    inputs: BaseInputs
  ): Future[Either[ScalaCliError, (BspClient.BuildOutput, Int => Int)]] = {

    val (instrumentedInput, lineMapping) = InstrumentedInputs(inputs) match {
      case Right(value) => (value.inputs, value.lineMapping)
      case Left(value)  =>
        log.error(s"Error while instrumenting: $value")
        (inputs, identity: Int => Int)
    }

    Files.write(scalaMain, instrumentedInput.code.getBytes)
    bspClient
      .build(snippetId.base64UUID, inputs.isWorksheetMode, inputs.target)
      .value
      .recover {
        case timeout: TimeoutException => BspTaskTimeout("Build Server Timeout Exception").asLeft
        case err                       => InternalBspError(err.getMessage).asLeft
      }
      .map {
        case Right(buildOutput)                  => Right((buildOutput, lineMapping))
        case Left(CompilationError(diagnostics)) =>
          val mapped = diagnostics.map { p =>
            val orig = p.line
            val mapped = orig.map(lineMapping)
            p.copy(line = mapped)
          }
          Left(CompilationError(mapped))
        case Left(other) => Left(other)
      }
  }

  def runForked(
    bspRun: BspClient.BuildOutput,
    isWorksheet: Boolean,
    onOutput: ProcessOutput => Any,
    lineMapping: Int => Int = identity
  ): Future[Either[ScastieRuntimeError, RunOutput]] = {
    val outputBuffer: ListBuffer[String] = ListBuffer()
    val instrumentations: AtomicReference[List[Instrumentation]] = new AtomicReference(List())
    val runtimeError: AtomicReference[Option[org.scastie.runtime.api.RuntimeError]] = new AtomicReference(None)

    def forwardAndStorePrint(str: String, tpe: ProcessOutputType) = {
      val maybeRuntimeError = decode[org.scastie.runtime.api.RuntimeError](str)
      val maybeInstrumentation = decode[List[Instrumentation]](str)

      maybeRuntimeError.foreach { error =>
        val mappedLine = error.line.map(lineMapping)
        runtimeError.set(Some(error.copy(line = mappedLine)))
      }
      maybeInstrumentation.foreach(instrumentations.set(_))

      if (maybeRuntimeError.isLeft && maybeInstrumentation.isLeft) {
        outputBuffer.append(str)
        onOutput(ProcessOutput(line = str, tpe = tpe, id = None))
      }
    }

    val runProcess = bspRun.process.run(
      ProcessLogger.apply(
        (fout: String) => forwardAndStorePrint(fout, ProcessOutputType.StdOut),
        (ferr: String) => forwardAndStorePrint(ferr, ProcessOutputType.StdErr)
      )
    )

    val processResult =
      CompletableFuture.supplyAsync { () => runProcess.exitValue() }.orTimeout(10, TimeUnit.SECONDS).asScala
    processResult.onComplete(_ => runProcess.destroy())
    processResult
      .map { exitCode =>
        Right(RunOutput(instrumentations.get, bspRun.diagnostics, runtimeError.get, exitCode))
      }
      .recover {
        case _: TimeoutException =>
          forwardAndStorePrint("Timeout exceeded.", ProcessOutputType.StdErr)
          Left(RuntimeTimeout("Timeout exceeded."))
        case err =>
          forwardAndStorePrint(s"Unknown exception $err", ProcessOutputType.StdErr)
          Left(InternalRuntimeError(s"Unknown exception $err"))
      }
  }

  def restart(): Unit = {
    bspClient.end()
    bspClient = new BspClient(coloredStackTrace, workingDir, compilationTimeout, reloadTimeout)
  }

  def end(): Unit = bspClient.end()
}
