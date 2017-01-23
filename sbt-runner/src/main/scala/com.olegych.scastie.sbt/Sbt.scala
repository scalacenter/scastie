package com.olegych.scastie
package sbt

import BuildInfo.{version => buildVersion}

import api._

import scala.util.Random
import System.{lineSeparator => nl}
import org.slf4j.LoggerFactory

import java.nio.file._
import java.io.IOException

class Sbt() {
  private val log = LoggerFactory.getLogger(getClass)

  private val sbtDir = Files.createTempDirectory("scastie")

  private val uniqueId = Random.alphanumeric.take(10).mkString

  private var currentSbtConfig = ""
  private var currentSbtPluginConfig = ""

  private val sbtConfigFile = sbtDir.resolve("config.sbt")
  private val prompt = s"""shellPrompt := (_ => "$uniqueId\\n")"""
  write(sbtConfigFile, prompt)

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)

  write(projectDir.resolve("build.properties"), s"sbt.version = 0.13.13")

  private val pluginFile = projectDir.resolve("plugins.sbt")
  write(pluginFile,
        s"""addSbtPlugin("org.scastie" % "sbt-scastie" % "$buildVersion")""")

  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")

  Files.createDirectories(codeFile.getParent)

  private val (process, fin, fout) = {
    val builder = new ProcessBuilder("sbt").directory(sbtDir.toFile)
    val currentOpts = sys.env.get("SBT_OPTS").getOrElse("")
    builder
      .environment()
      .put(
        "SBT_OPTS",
        Seq(
          currentOpts,
          "-Djline.terminal=jline.UnsupportedTerminal",
          "-Dsbt.log.noformat=true"
        ).mkString(" ")
      )

    val process = builder.start()

    (process, process.getOutputStream, process.getInputStream)
  }

  private def collect(lineCallback: (String, Boolean) => Unit): Unit = {
    val chars = new collection.mutable.Queue[Character]()
    var read = 0
    var prompt = false
    while (read != -1 && !prompt) {
      read = fout.read()
      if (read == 10) {
        val line = chars.mkString
        prompt = line == uniqueId

        // log.info(line)
        println(line)

        lineCallback(line, prompt)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
  }

  collect((_, _) => ())

  private def process(command: String,
                      lineCallback: (String, Boolean) => Unit): Unit = {
    fin.write((command + nl).getBytes)
    fin.flush()
    collect(lineCallback)
  }

  def close(): Unit = {
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

  def eval(command: String,
           inputs: Inputs,
           lineCallback: (String, Boolean) => Unit): Unit = {

    val configChange = needsReload(inputs)

    if (inputs.sbtConfig != currentSbtConfig) {
      write(sbtConfigFile, prompt + nl + inputs.sbtConfig, truncate = true)
      currentSbtConfig = inputs.sbtConfig
    }

    if (inputs.sbtPluginsConfig != currentSbtPluginConfig) {
      write(pluginFile, inputs.sbtPluginsConfig, truncate = true)
      currentSbtPluginConfig = inputs.sbtPluginsConfig
    }

    if (configChange) {
      process("reload", lineCallback)
    }

    write(codeFile, inputs.code, truncate = true)
    try {
      process(command, lineCallback)
    } catch {
      case e: IOException => {
        // when the paste is pkilled (timeout) the sbt output stream is closed
        if (e.getMessage == "Stream closed") ()
        else throw e
      }
    }
  }
}
