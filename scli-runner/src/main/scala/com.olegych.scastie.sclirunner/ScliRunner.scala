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


object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
}

class ScliRunner {
  import ScliRunner._

  private val log = Logger("ScliRunner")

  // TODO: working dir, setup directory, is it that useful except for multiple files?
  private val workingDir = Files.createTempDirectory("scastie")

  
  def runTask(task: ScliTask, onSuccess: String => Any): Option[InstrumentationFailureReport] = {
    log.info(s"Running task $task")

    // Instrumentation
    var input = InstrumentedInputs(task.inputs)

    input match {
      case Left(failure) => Some(failure)
      case Right(notFinalCode) => {
        // notFinalCode
        val runtimeDependency = task.inputs.target.runtimeDependency.map(x => Set(x))

        val code = (task.inputs.libraries ++ runtimeDependency.getOrElse(Set()))
          .map(scalaDepToFullName).map(s => s"//> using lib \"$s\"").mkString + "\n" + notFinalCode


        None
      }
    }
  }

  // Process streams
  private var pStdin: Option[OutputStream] = None
  private var pStdout: Option[InputStream] = None
  private var pStderr: Option[InputStream] = None

  private def startBsp : Unit = {
    log.info("Starting Scala-CLI BSP")
    val processBuilder: ProcessBuilder = Process(Seq("scala-cli", "bsp", "."), workingDir.toFile())
    val io = BasicIO.standard(true)
      .withInput(i => pStdin = Some(i))
      .withError(e => pStderr = Some(e))
      .withOutput(o => pStdout = Some(o))

    // Just a quick look
    processBuilder.run(io)
  }

  private def scalaDepToFullName = (dep: ScalaDependency) => s"${dep.groupId}::${dep.artifact}:${dep.version}"


  startBsp
}
