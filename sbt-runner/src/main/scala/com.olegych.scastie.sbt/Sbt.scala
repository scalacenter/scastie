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
  copyDir(src = Paths.get("../sbt-template"), dst = sbtDir)

  private val uniqueId = Random.alphanumeric.take(10).mkString
  write(sbtDir.resolve("build.sbt"),
        s"""shellPrompt := (_ => "$uniqueId\\n")""")

  private val sbtConfigFile = sbtDir.resolve("scastie/config.sbt")
  var currentSbtConfig      = read(sbtConfigFile).getOrElse("")

  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")

  private val (process, fin, fout) = {
    val builder = new ProcessBuilder("sbt").directory(sbtDir.toFile)
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
        println(line)
        // log.info(" sbt: " + line)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
  }

  collect((line, _) => ())

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
    if (paste.sbtConfig != currentSbtConfig) {
      write(sbtConfigFile, paste.sbtConfig, truncate = true)
      currentSbtConfig = paste.sbtConfig
      process("reload", lineCallback)
    }

    write(codeFile, paste.code, truncate = true)
    process(command, lineCallback)
  }
}
