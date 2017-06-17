package com.olegych.scastie
package sbt

import api._

import scala.util.Random
import System.{lineSeparator => nl}
import org.slf4j.LoggerFactory

import java.nio.file._
import java.io.{IOException, BufferedReader, InputStreamReader}
import java.nio.charset.StandardCharsets

class Sbt(defaultConfig: Inputs, sbtDir: Path = Files.createTempDirectory("scastie")) {

  private val log = LoggerFactory.getLogger(getClass)

  private val uniqueId = Random.alphanumeric.take(10).mkString

  private var currentSbtConfig = ""
  private var currentSbtPluginConfig = ""

  private val buildFile = sbtDir.resolve("build.sbt")
  private val prompt = s"""shellPrompt := (_ => "$uniqueId\\n")"""

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)

  write(projectDir.resolve("build.properties"), s"sbt.version = 0.13.15")

  private val pluginFile = projectDir.resolve("plugins.sbt")

  private def setup(): Unit = {
    setConfig(defaultConfig)
    setPlugins(defaultConfig)
  }

  setup()

  val codeFile = sbtDir.resolve("src/main/scala/main.scala")
  Files.createDirectories(codeFile.getParent)

  def scalaJsContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.targetFilename))
  }

  def scalaJsSourceMapContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.sourceMapFilename))
  }

  private val (process, fin, fout) = {

    val builder = new ProcessBuilder("sbt").directory(sbtDir.toFile)
    builder
      .environment()
      .put(
        "SBT_OPTS",
        Seq(
          "-Xms512m",
          "-Xmx1g",
          "-Djline.terminal=jline.UnsupportedTerminal",
          "-Dsbt.log.noformat=true"
        ).mkString(" ")
      )

    val process = builder.start()

    val in = new BufferedReader(
      new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8)
    )

    (process, process.getOutputStream, in)
  }

  private def collect(
      lineCallback: (String, Boolean, Boolean, Boolean) => Unit,
      reload: Boolean
  ): Boolean = {
    val chars = new collection.mutable.Queue[Character]()
    var read = 0
    var done = false
    var sbtError = false

    while (read != -1 && !done) {
      read = fout.read()
      if (read == 10) {
        val line = chars.mkString

        val prompt = line == uniqueId
        sbtError = line == "[error] Type error in expression"
        done = prompt || sbtError

        log.info(line)

        lineCallback(line, done, sbtError, reload)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
    if (sbtError) {
      setup()
      process("r", noop, reload = false)
    }

    sbtError
  }

  type LineCallback = (String, Boolean, Boolean, Boolean) => Unit
  val noop: LineCallback =
    (_, _, _, _) => ()

  collect(noop, reload = false)

  private def process(command: String,
                      lineCallback: LineCallback,
                      reload: Boolean): Boolean = {
    fin.write((command + nl).getBytes)
    fin.flush()
    collect(lineCallback, reload)
  }

  def kill(): Unit = {
    val pidField = process.getClass.getDeclaredField("pid")
    pidField.setAccessible(true)
    val pid = pidField.get(process).asInstanceOf[Int]
    import sys.process._
    s"pkill -KILL -P $pid".!
    ()
  }

  def needsReload(inputs: Inputs): Boolean =
    inputs.sbtConfig != currentSbtConfig ||
      inputs.sbtPluginsConfig != currentSbtPluginConfig

  def exit(): Unit = {
    process("exit", noop, reload = false)
    ()
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  private def setPlugins(inputs: Inputs): Unit = {
    writeFile(pluginFile, inputs.sbtPluginsConfig)
    currentSbtPluginConfig = inputs.sbtPluginsConfig
  }

  private def setConfig(inputs: Inputs): Unit = {
    writeFile(buildFile, prompt + nl + inputs.sbtConfig)
    currentSbtConfig = inputs.sbtConfig
  }

  def eval(command: String,
           inputs: Inputs,
           lineCallback: LineCallback,
           reload: Boolean): Boolean = {
    maybeReloadAndEval(command = command,
                       commandIfNeedsReload = "",
                       inputs,
                       lineCallback,
                       reload)
  }

  def evalIfNeedsReload(command: String,
                        inputs: Inputs,
                        lineCallback: LineCallback,
                        reload: Boolean): Boolean = {
    maybeReloadAndEval(command = "",
                       commandIfNeedsReload = command,
                       inputs,
                       lineCallback,
                       reload)
  }

  private def maybeReloadAndEval(command: String,
                                 commandIfNeedsReload: String,
                                 inputs: Inputs,
                                 lineCallback: LineCallback,
                                 reload: Boolean) = {

    val isReloading = needsReload(inputs)

    if (inputs.sbtConfig != currentSbtConfig) {
      setConfig(inputs)
    }

    if (inputs.sbtPluginsConfig != currentSbtPluginConfig) {
      setPlugins(inputs)
    }

    val reloadError =
      if (isReloading) {
        process("reload", lineCallback, reload = true)
      } else false

    if (!reloadError) {
      write(codeFile, inputs.code, truncate = true)
      try {
        if (isReloading && !commandIfNeedsReload.isEmpty)
          process(commandIfNeedsReload, lineCallback, reload)
        if (!command.isEmpty) process(command, lineCallback, reload)
      } catch {
        case e: IOException => {
          // when the snippet is pkilled (timeout) the sbt output stream is closed
          if (e.getMessage == "Stream closed") ()
          else throw e
        }
      }
    }

    reloadError
  }
}
