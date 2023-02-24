package com.olegych.scastie.sclirunner

import scala.collection.immutable.Queue
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs
import com.typesafe.scalalogging.Logger
import java.nio.file.Files

// Process interaction
import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}
import com.olegych.scastie.instrumentation.Instrument
import com.olegych.scastie.instrumentation.InstrumentationFailure
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.instrumentation.InstrumentationFailureReport
import com.olegych.scastie.instrumentation.InstrumentedInputs
import java.io.OutputStream
import java.io.InputStream
import ch.epfl.scala.bsp4j.BuildClient
import ch.epfl.scala.bsp4j.LogMessageParams
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import ch.epfl.scala.bsp4j.ShowMessageParams
import ch.epfl.scala.bsp4j.DidChangeBuildTarget
import ch.epfl.scala.bsp4j.TaskFinishParams
import ch.epfl.scala.bsp4j.TaskProgressParams
import ch.epfl.scala.bsp4j.TaskStartParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.InitializeBuildParams
import ch.epfl.scala.bsp4j.BuildClientCapabilities
import java.util.Collections
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.RunParams

object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
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

  initFiles

  
  def runTask(task: ScliTask, onSuccess: String => Any): Option[InstrumentationFailureReport] = {
    log.info(s"Running task $task")

    // Instrumentation
    var input = InstrumentedInputs(task.inputs)

    input match {
      case Left(failure) => Some(failure)
      case Right(InstrumentedInputs(notFinalCode, isForcedProgramMode)) => {
        // notFinalCode
        val runtimeDependency = task.inputs.target.runtimeDependency.map(x => Set(x))

        val code = (task.inputs.libraries ++ runtimeDependency.getOrElse(Set()))
          .map(scalaDepToFullName).map(s => s"//> using lib \"$s\"").mkString + "\n" + notFinalCode.code


        writeFile(scalaMain, code)
        bspClient.build
        None
      }
    }
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
}
