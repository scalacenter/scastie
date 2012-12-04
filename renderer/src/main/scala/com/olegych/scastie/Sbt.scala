package com.olegych.scastie

import java.io.File
import org.apache.commons.lang3.SystemUtils
import akka.event.LoggingAdapter
import akka.util.BoundedBlockingQueue
import java.util
import org.apache.commons.collections15.buffer.CircularFifoBuffer

/**
  */
case class Sbt(dir: File, log: LoggingAdapter, uniqueId: String = ">") {

  private val (process, fin, input, fout, output) = {
    def absolutePath(command: String) = new File(command).getAbsolutePath
    val builder = new ProcessBuilder(absolutePath(if (SystemUtils.IS_OS_WINDOWS) "xsbt.cmd" else "xsbt.sh"))
        .directory(dir)
    val currentOpts = Option(System.getenv("SBT_OPTS")).getOrElse("")
        .replaceAll("-agentlib:jdwp=transport=dt_shmem,server=n,address=.*,suspend=y", "")
    builder.environment()
        .put("SBT_OPTS", currentOpts + " -Djline.terminal=jline.UnsupportedTerminal -Dsbt.log.noformat=true -Xmx412M -XX:MaxPermSize=100m" )
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
    log.info("Executing {}", command)
    input.write(command + "\n")
    fin.flush()
    if (waitForPrompt) {
      this.waitForPrompt
    } else {
      Seq("")
    }
  }

  def waitForPrompt: Seq[String] = {
    import collection.JavaConversions._
    val lines = new CircularFifoBuffer[String](200)
    val chars = new CircularFifoBuffer[Character](1000)
    var read: Int = 0
    while (read != -1 && lines.lastOption != Some(uniqueId)) {
      read = fout.read()
      if (read == 10) {
        lines.add(chars.mkString)
        log.info("sbt: " + lines.last)
        chars.clear()
      } else {
        chars.add(read.toChar)
      }
    }
    lines.dropRight(1).toSeq
  }

  def close() {
    try process("exit", waitForPrompt = false) catch {
      case e: Throwable => log.error(e, "Error while soft exit")
    }
    process.destroy()
  }

  object Success {
    val SuccessParser = """(?s)\[success\].*""".r
    def unapply(result: Seq[String]): Option[String] = result.lastOption match {
      case Some(SuccessParser()) => Option(resultAsString(result))
      case _ => None
    }
  }

  object NotTopLevelExpression {
    val ErrorParser =
      """(?s)^\[error\].*(?:expected class or object definition|expected start of definition).*""".r
    def unapply(result: Seq[String]): Option[String] = result.collectFirst {
      case ErrorParser() => resultAsString(result)
    }
  }

  def resultAsString(result: Seq[String]) = result.mkString("\n").replaceAll(uniqueId, "")
}
