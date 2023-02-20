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


object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
}

class ScliRunner {
  import ScliRunner._

  private val log = Logger("ScliRunner")

  // TODO: working dir, setup directory, is it that useful except for multiple files?
  private val workingDir = Files.createTempDirectory("scastie")

  
  def runTask(task: ScliTask): Either[InstrumentationFailure, Unit] = {
    log.info(s"Running task $task")

    // Instrumentation
    var input = Instrument(task.inputs.code, task.inputs.target)

    input match {
      case Left(failure) => Left(failure)
      case Right(notFinalCode) => {
        // notFinalCode
        val runtimeDependency = task.inputs.target.runtimeDependency.map(x => Set(x))

        val code = (task.inputs.libraries ++ runtimeDependency.getOrElse(Set()))
          .map(scalaDepToFullName).map(s => s"//> using lib \"$s\"").mkString + "\n" + notFinalCode

        val processBuilder: ProcessBuilder = Process("scala-cli _ --main-class Main")
        val io = BasicIO.standard(true)
          .withInput(write => {
            write.write(code.getBytes(StandardCharsets.UTF_8))
            write.close()
          })
          .withError(error => {
            log.info(s"-- stderr: ${IOSource.fromInputStream(error).mkString}")
          })
          .withOutput(output => {
            val outputString = 
            log.info(s"-- stdout : ${IOSource.fromInputStream(output).mkString}")
            // TODO: handle correctly lol
          })

        // Just a quick look
        processBuilder.run(io)
        Right(())
      }
    }
  }

  def scalaDepToFullName = (dep: ScalaDependency) => s"${dep.groupId}::${dep.artifact}:${dep.version}"
}
