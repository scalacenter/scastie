package com.olegych.scastie

import java.io.File
import collection.mutable.ListBuffer
import org.apache.commons.lang3.SystemUtils

/**
  */
case class Sbt(dir: File) {

  private val (process, fin, input, fout, output) = {
    def absolutePath(command: String) = new File(command).getAbsolutePath
    val builder = new ProcessBuilder(absolutePath(if (SystemUtils.IS_OS_WINDOWS) "xsbt.cmd" else "xsbt.sh"))
    builder.environment()
        .put("SBT_OPTS", "-Djline.terminal=jline.UnsupportedTerminal -Dsbt.log.noformat=true")
    val process = builder.directory(dir).start()
    import scalax.io.JavaConverters._
    //can't use .lines since it eats all input
    (process,
        process.getOutputStream, process.getOutputStream.asUnmanagedOutput,
        process.getInputStream, process.getInputStream.asUnmanagedInput.bytes)
  }
  waitForPrompt

  def process(command: String, waitForPrompt:Boolean = true) = {
    input.write(command + "\n")
    fin.flush()
    if (waitForPrompt){
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
//        println(lines.last)
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
