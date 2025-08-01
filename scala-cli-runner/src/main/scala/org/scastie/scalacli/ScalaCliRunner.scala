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

class ScalaCliRunner(coloredStackTrace: Boolean, workingDir: Path) {

  private val log = Logger("ScalaCliRunner")
  private val bspClient = new BspClient(coloredStackTrace, workingDir)
  private val scalaMain = workingDir.resolve("Main.scala")
  Files.createDirectories(scalaMain.getParent())

  def runTask(snippetId: SnippetId, inputs: ScalaCliInputs, timeout: FiniteDuration, onOutput: ProcessOutput => Any): Future[Either[ScalaCliError, RunOutput]] = {
    log.info(s"Running task with snippetId=$snippetId")
    build(snippetId, inputs).flatMap {
      case Right(value) => runForked(value, inputs.isWorksheetMode, onOutput)
      case Left(value) => Future.successful(Left[ScalaCliError, RunOutput](value))
    }
  }

  def build(
    snippetId: SnippetId,
    inputs: BaseInputs,
  ): Future[Either[ScalaCliError, BspClient.BuildOutput]] = {

    val instrumentedInput = InstrumentedInputs(inputs) match {
      case Right(value) => value.inputs
      case Left(value) =>
        log.error(s"Error while instrumenting: $value")
        inputs
    }

    Files.write(scalaMain, instrumentedInput.code.getBytes)
    bspClient.build(snippetId.base64UUID, inputs.isWorksheetMode, inputs.target).value.recover {
      case timeout: TimeoutException => BspTaskTimeout("Build Server Timeout Exception").asLeft
      case err => InternalBspError(err.getMessage).asLeft
    }
  }

  def runForked(bspRun: BspClient.BuildOutput, isWorksheet: Boolean, onOutput: ProcessOutput => Any): Future[Either[ScastieRuntimeError, RunOutput]] = {
    val outputBuffer: ListBuffer[String] = ListBuffer()
    val instrumentations: AtomicReference[List[Instrumentation]] = new AtomicReference(List())
    val runtimeError: AtomicReference[Option[org.scastie.runtime.api.RuntimeError]] = new AtomicReference(None)

    def forwardAndStorePrint(str: String, tpe: ProcessOutputType) = {
      val maybeRuntimeError = decode[org.scastie.runtime.api.RuntimeError](str)
      val maybeInstrumentation = decode[List[Instrumentation]](str)

      maybeRuntimeError.foreach(error => runtimeError.set(Some(error)))
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

    val processResult = CompletableFuture.supplyAsync { () => runProcess.exitValue() }.orTimeout(10, TimeUnit.SECONDS).asScala
    processResult.onComplete(_ => runProcess.destroy())
    processResult.map { exitCode =>
      Right(RunOutput(instrumentations.get, bspRun.diagnostics, runtimeError.get, exitCode))
    }.recover {
      case _: TimeoutException =>
        forwardAndStorePrint("Timeout exceeded.", ProcessOutputType.StdErr)
        Left(RuntimeTimeout("Timeout exceeded."))
      case err =>
        forwardAndStorePrint(s"Unknown exception $err", ProcessOutputType.StdErr)
        Left(InternalRuntimeError(s"Unknown exception $err"))
    }
  }

  def end: Unit = bspClient.end
}
