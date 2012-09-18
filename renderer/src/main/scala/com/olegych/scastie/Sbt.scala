package com.olegych.scastie

import java.io.File
import collection.mutable.ListBuffer
import org.apache.commons.lang3.SystemUtils
import akka.event.LoggingAdapter

/**
  */
case class Sbt(dir: File, log: LoggingAdapter) {

  private val (process, fin, input, fout, output) = {
    def absolutePath(command: String) = new File(command).getAbsolutePath
    val builder = new ProcessBuilder(absolutePath(if (SystemUtils.IS_OS_WINDOWS) "xsbt.cmd" else "xsbt.sh"))
        .directory(dir)
    val currentOpts = Option(System.getenv("SBT_OPTS")).getOrElse("")
        .replaceAll("-agentlib:jdwp=transport=dt_shmem,server=n,address=.*,suspend=y", "")
    builder.environment()
        .put("SBT_OPTS", currentOpts + " -Djline.terminal=jline.UnsupportedTerminal -Dsbt.log.noformat=true")
    log.info("Starting sbt with {} {} {}", builder.command(), builder.environment(), builder.directory())
    val process = builder.start()
    import scalax.io.JavaConverters._
    //can't use .lines since it eats all input
    (process,
        process.getOutputStream, process.getOutputStream.asUnmanagedOutput,
        process.getInputStream, process.getInputStream.asUnmanagedInput.bytes)
  }
  waitForPrompt

  def process(command: String, waitForPrompt: Boolean = true) = {
    input.write(command + "\n")
    fin.flush()
    if (waitForPrompt) {
      this.waitForPrompt
    } else {
      ""
    }
  }

  def waitForPrompt = {
    //    val lines = Stream.continually {
    //      Stream.continually(fout.read()).takeWhile(read => read != 10.toByte).map(_.toChar).mkString
    ////      output.takeWhile(_ != 10.toByte).map(_.toChar).mkString
    //    }
    //    lines.takeWhile(_ != ">").mkString("\n")
    val lines = ListBuffer[String]()
    val chars = ListBuffer[Char]()
    var read: Int = 0
    while (read != -1 && lines.lastOption != Some(">")) {
      read = fout.read()
      if (read == 10) {
        lines += chars.mkString
        log.info("sbt: " + lines.last)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
    lines.dropRight(1).mkString("\n")
  }

  def f1 = {
    val lines = Stream.continually {
      Stream.continually(fout.read()).takeWhile(_ != 10).map(_.toChar).mkString
    }
    lines.takeWhile(_ != ">").mkString("\n")
  }

  def f2 = {
    val lines = ListBuffer[String]()
    val chars = ListBuffer[Char]()
    var read: Int = 0
    while (read != -1 && lines.lastOption != Some(">")) {
      read = fout.read()
      if (read == 10) {
        lines += chars.mkString
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
    lines.dropRight(1).mkString("\n")
  }

  def close() {
    process("exit", waitForPrompt = false)
    process.destroy()
  }
}
