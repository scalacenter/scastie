package com.olegych.scastie.sclirunner

import com.olegych.scastie.api.{SnippetId, Inputs, ScalaDependency}
import com.olegych.scastie.instrumentation.{InstrumentedInputs, InstrumentationFailureReport}
import com.typesafe.scalalogging.Logger
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.CompletableFuture
import java.io.{InputStream, OutputStream}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import com.olegych.scastie.instrumentation.InstrumentationFailure
import com.olegych.scastie.api.Problem
import com.olegych.scastie.instrumentation.Instrument
import play.api.libs.json.Reads
import play.api.libs.json.Json
import scala.util.control.NonFatal
import com.olegych.scastie.api.Instrumentation
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.util.concurrent.TimeoutException
import scala.collection.concurrent.TrieMap


object ScliRunner {
  case class ScliRun(
    output: List[String],
    instrumentation: Option[List[Instrumentation]] = None,
    diagnostics: List[Problem] = List()
  )

  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])

  // Errors
  trait ScliRunnerError extends Exception
  case class InstrumentationException(failure: InstrumentationFailureReport) extends ScliRunnerError
  case class CompilationError(problems: List[Problem], logs: List[String] = List()) extends ScliRunnerError
  // From Bsp
  case class ErrorFromBsp(err: BspClient.BspError, logs: List[String] = List()) extends ScliRunnerError
}

class ScliRunner {
  import ScliRunner._

  private val log = Logger("ScliRunner")

  // Files
  private val workingDir = Files.createTempDirectory("scastie")
  private val scalaMain = workingDir.resolve("src/main/scala/main.scala")

  private def initFiles : Unit = {
    Files.createDirectories(scalaMain.getParent())
    writeFile(scalaMain, "@main def main = { println(\"hello world!\") } ")
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  def runTask(task: ScliTask, onOutput: String => Any): Future[Either[ScliRun, ScliRunnerError]] = {
    log.info(s"Running task with snippetId=${task.snippetId}")

    // Extract directives from user code
    val (userDirectives, userCode) = task.inputs.code.split("\n")
      .span(line => line.startsWith("//>"))

    // Instrument
    InstrumentedInputs(task.inputs.copy(code = userCode.mkString("\n"))) match {
      case Left(failure) => Future.failed(InstrumentationException(failure))
      case Right(InstrumentedInputs(inputs, isForcedProgramMode)) =>
        buildAndRun(task.snippetId, inputs, isForcedProgramMode, userDirectives, userCode, onOutput)
    }
  }

  def buildAndRun(snippetId: SnippetId, inputs: Inputs, isForcedProgramMode: Boolean, userDirectives: Array[String], userCode: Array[String], onOutput: String => Any)
      : Future[Either[ScliRun, ScliRunnerError]] = {
    val runtimeDependency = inputs.target.runtimeDependency.map(Set(_)).getOrElse(Set()) ++ inputs.libraries
    val allDirectives = (runtimeDependency.map(scalaDepToFullName).map(libraryDirective) ++ userDirectives)
    val totalLineOffset = -runtimeDependency.size + Instrument.getExceptionLineOffset(inputs)

    val charOffsetInstrumentation = userDirectives.map(_.length() + 1).sum

    val code = allDirectives.mkString("\n") + "\n" + inputs.code
    writeFile(scalaMain, code)

    var instrumentationMem: Option[List[Instrumentation]] = None
    var outputBuffer: List[String] = List()

    def forwardPrint(str: String) = {
      outputBuffer = str :: outputBuffer
      onOutput(str)
    }

    def mapProblems(list: List[Problem]): List[Problem] = {
      list.map(pb =>
        pb.copy(line = pb.line.map(line => {
          if (line <= allDirectives.size) // if issue is on directive, then do not map.
            line
          else
            line + totalLineOffset + 1
        }))
      )
    }

    def handleError(bspError: BspClient.BspError): ScliRunnerError = bspError match {
      case x: BspClient.CompilationError => CompilationError(mapProblems(x.toProblemList), x.logs)
      case _ => ErrorFromBsp(bspError, bspError.logs)
    }

    // Should be executed asynchronously due to the timeout (executed synchronously)
    def runProcess(bspRun: BspClient.BspRun) = {
      // print log messages
      bspRun.logMessages.foreach(forwardPrint)

      val runProcess = bspRun.process.run(
        ProcessLogger({ line: String => {
          // extract instrumentation
          extract[List[Instrumentation]](line) match {
            case None => forwardPrint(line)
            case Some(value) => {
              instrumentationMem = Some(value.map(inst => inst.copy(
                position = inst.position.copy(inst.position.start + charOffsetInstrumentation, inst.position.end + charOffsetInstrumentation)
              )))
            }
          }

        }})
      )
      javaProcesses.put(snippetId, runProcess)

      // Wait
      val f = Future { runProcess.exitValue() }
      val didSucceed =
        Try(Await.result(f, Duration(30, TimeUnit.SECONDS))) match {
          case Success(value) => {
            forwardPrint(s"Process exited with error code $value")
            true
          }
          case Failure(_: TimeoutException) => {
            forwardPrint("Timeout exceeded.")
            false
          }
          case Failure(e) => {
            forwardPrint(s"Unknown exception $e")
            false
          }
        }

      if (!didSucceed) {
        runProcess.destroy()
      }
      javaProcesses.remove(snippetId)

      ScliRun(outputBuffer, instrumentationMem, mapProblems(bspRun.toProblemList))
    }

    val build = bspClient.build(snippetId.base64UUID)
    build.map { result =>
      result match {
        case Right(bspError) => Right(handleError(bspError))
        case Left(x: BspClient.BspRun) => Left(runProcess(x))
      }
    }
  }

  def end: Unit = {
    bspClient.end
    javaProcesses.values.foreach(_.destroy())
    process.map(_.destroy())
  }

  // Java processes
  private val javaProcesses = TrieMap[SnippetId, Process]() // mutable and concurrent HashMap

  // Process streams
  private var pStdin: Option[OutputStream] = None
  private var pStdout: Option[InputStream] = None
  private var pStderr: Option[InputStream] = None
  private var process: Option[Process] = None
  
  // Bsp
  private val bspClient = {
    log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")
    val processBuilder: ProcessBuilder = Process(Seq("scala-cli", "bsp", ".", "-deprecation"), workingDir.toFile())
    val io = BasicIO.standard(true)
      .withInput(i => pStdin = Some(i)) 
      .withError(e => pStderr = Some(e))
      .withOutput(o => pStdout = Some(o))

    process = Some(processBuilder.run(io))

    // TODO: really bad
    while (pStdin.isEmpty || pStdout.isEmpty || pStderr.isEmpty) Thread.sleep(100)

    // Create BSP connection
    new BspClient(workingDir, pStdout.get, pStdin.get)
  }

  private val runTimeScala = "//> using lib \"org.scastie::runtime-scala\""

  private def scalaDepToFullName = (dep: ScalaDependency) => s"${dep.groupId}::${dep.artifact}:${dep.version}"
  private def libraryDirective = (lib: String) => s"//> using lib \"$lib\"".mkString

  initFiles

  private def extract[T: Reads](line: String) = {
    try {
      Json.fromJson[T](Json.parse(line)).asOpt
    } catch {
      case NonFatal(e) => None
    }
  }
}