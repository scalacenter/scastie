package com.olegych.scastie.sclirunner

import scala.sys.process._
import com.typesafe.scalalogging.Logger
import java.nio.file.{Files, Path, StandardOpenOption}
import com.olegych.scastie.api.{SnippetId, Inputs, ScalaDependency}
import java.util.concurrent.CompletableFuture
import com.olegych.scastie.instrumentation.{InstrumentedInputs, InstrumentationFailureReport}
import java.io.{InputStream, OutputStream}

object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
  case class ScliTaskResultContent()

  type ScliTaskResult = Either[ScliTaskResultContent, InstrumentationFailureReport]
}

class ScliRunner {
  import ScliRunner._

  private val log = Logger("ScliRunner")

  // Files
  private val workingDir = Files.createTempDirectory("scastie")
  private val scalaMain = workingDir.resolve("src/main/scala/main.scala")

  private def initFiles : Unit = {
    Files.createDirectories(scalaMain.getParent())
    writeFile(scalaMain, "@main def main = println(\"hello world!\")")
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  def runTask(task: ScliTask): CompletableFuture[ScliTaskResult] = {
    val future = new CompletableFuture[ScliTaskResult]()
    log.info(s"Running task with snippetId=${task.snippetId}")

    // Instrument
    InstrumentedInputs(task.inputs) match {
      case Left(failure) => future.complete(Right(failure))
      case Right(InstrumentedInputs(inputs, isForcedProgramMode)) => {
        val runtimeDependency = task.inputs.target.runtimeDependency.map(Set(_)).getOrElse(Set()) ++ task.inputs.libraries

        val code = runtimeDependency.map(scalaDepToFullName).map(libraryDirective).mkString("\n") + "\n" + inputs.code
        writeFile(scalaMain, code)
        val build = bspClient.build(task.snippetId.base64UUID) // TODO: use a fresh identifier
        
      }
    }

    future
  }

  // Process streams
  private var pStdin: Option[OutputStream] = None
  private var pStdout: Option[InputStream] = None
  private var pStderr: Option[InputStream] = None
  
  // Bsp
  private val bspClient = {
    log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")
    val processBuilder: ProcessBuilder = Process(Seq("scala-cli", "bsp", "."), workingDir.toFile())
    val io = BasicIO.standard(true)
      .withInput(i => pStdin = Some(i)) 
      .withError(e => pStderr = Some(e))
      .withOutput(o => pStdout = Some(o))

    val process = processBuilder.run(io)

    // TODO: really bad
    while (pStdin.isEmpty || pStdout.isEmpty || pStderr.isEmpty) Thread.sleep(100)

    // Create BSP connection
    new BspClient(workingDir, pStdout.get, pStdin.get)
  }

  private def scalaDepToFullName = (dep: ScalaDependency) => s"${dep.groupId}::${dep.artifact}:${dep.version}"
  private def libraryDirective = (lib: String) => s"//> using lib \"$lib\"".mkString

  initFiles
}