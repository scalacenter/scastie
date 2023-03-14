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

object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
  case class InstrumentationException(failure: InstrumentationFailureReport) extends Exception

  case class CompilationError(problems: List[Problem]) extends Exception
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

  def runTask(task: ScliTask, onOutput: String => Any): Future[BspClient.BspClientRun] = {
    log.info(s"Running task with snippetId=${task.snippetId}")

    // Extract directives from user code
    val (userDirectives, userCode) = task.inputs.code.split("\n")
      .span(line => line.startsWith("//>"))

    // Instrument
    InstrumentedInputs(task.inputs.copy(code = userCode.mkString("\n"))) match {
      case Left(failure) => Future.failed(InstrumentationException(failure))
      case Right(InstrumentedInputs(inputs, isForcedProgramMode)) => {
        val runtimeDependency = task.inputs.target.runtimeDependency.map(Set(_)).getOrElse(Set()) ++ task.inputs.libraries
        val allDirectives = (runtimeDependency.map(scalaDepToFullName).map(libraryDirective) ++ userDirectives)
        val totalOffset = -runtimeDependency.size + Instrument.getExceptionLineOffset(task.inputs)

        val charOffsetInstrumentation = userDirectives.map(_.length()).sum + 1

        val code = allDirectives.mkString("\n") + "\n" + inputs.code
        writeFile(scalaMain, code)

        var instrumentationMem: Option[List[Instrumentation]] = None

        val build = bspClient.build(task.snippetId.base64UUID, line => {
          extract[List[Instrumentation]](line) match { // extract instrumentation
            case None => onOutput(line)
            case Some(value) => {
              instrumentationMem = Some(value.map(inst => inst.copy(
                position = inst.position.copy(inst.position.start + charOffsetInstrumentation, inst.position.end + charOffsetInstrumentation)
              )))
            }
          }
        })

        build map { r =>
          r.copy(instrumentation = instrumentationMem)
        } recover {
          case x: BspClient.CompilationError => throw CompilationError(x.toProblemList.map(pb =>
            pb.copy(line = pb.line.map(_ + totalOffset + 1))))
          case other => throw other
        } 
      }
    }
  }

  def end: Unit = {
    bspClient.end
    process.map(_.destroy())
  }

  // Process streams
  private var pStdin: Option[OutputStream] = None
  private var pStdout: Option[InputStream] = None
  private var pStderr: Option[InputStream] = None
  private var process: Option[Process] = None
  
  // Bsp
  private val bspClient = {
    log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")
    val processBuilder: ProcessBuilder = Process(Seq("scala-cli", "bsp", "."), workingDir.toFile())
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