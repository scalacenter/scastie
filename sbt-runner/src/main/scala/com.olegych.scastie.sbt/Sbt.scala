package com.olegych.scastie
package sbt

import remote.RunPaste

import scala.util.Random
import System.{lineSeparator => nl}
import org.slf4j.LoggerFactory
import java.nio.file._

class Sbt() {
  private val log = LoggerFactory.getLogger(getClass)

  private val sbtDir = Files.createTempDirectory("scastie")

  private val uniqueId = Random.alphanumeric.take(10).mkString
  
  private var currentSbtConfig       = ""
  private var currentSbtPluginConfig = ""

  private val sbtConfigFile = sbtDir.resolve("config.sbt")
  private val prompt = s"""shellPrompt := (_ => "$uniqueId\\n")"""
  write(sbtConfigFile, prompt)

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)

  // bug with Dotty in 0.13.13 (https://github.com/sbt/sbt/issues/2895)
  write(projectDir.resolve("build.properties"), s"sbt.version = 0.13.11")

  private val pluginFile = projectDir.resolve("plugins.sbt")
  write(pluginFile, """addSbtPlugin("org.scastie" % "sbt-scastie" % "0.1.0-SNAPSHOT")""")

  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")

  Files.createDirectories(codeFile.getParent)
  
  private val (process, fin, fout) = {
    val builder     = new ProcessBuilder("sbt").directory(sbtDir.toFile)
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
    val chars  = new collection.mutable.Queue[Character]()
    var read   = 0
    var prompt = false
    while (read != -1 && !prompt) {
      read = fout.read()
      if (read == 10) {
        val line = chars.mkString
        prompt = line == uniqueId
        lineCallback(line, prompt)
        log.info(" sbt: " + line)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
  }

  collect((line, _) => log.info(" sbt: " + line))

  private def process(command: String,
                      lineCallback: (String, Boolean) => Unit): Unit = {
    fin.write((command + nl).getBytes)
    fin.flush()
    println("running command: " + command)
    collect(lineCallback)
  }

  def eval(command: String,
           paste: RunPaste,
           lineCallback: (String, Boolean) => Unit): Unit = {

    var configChange = false

    if (paste.sbtConfig != currentSbtConfig) {
      configChange = true
      write(sbtConfigFile, prompt + nl + paste.sbtConfig, truncate = true)
      currentSbtConfig = paste.sbtConfig
    }

    if (paste.sbtPluginsConfig != currentSbtPluginConfig) {
      configChange = true
      write(pluginFile, paste.sbtPluginsConfig, truncate = true)
      currentSbtPluginConfig = paste.sbtPluginsConfig
    }

    if (configChange) {
      process("reload", lineCallback)
    }

    write(codeFile, paste.code, truncate = true)
    process(command, lineCallback)
  }
}
