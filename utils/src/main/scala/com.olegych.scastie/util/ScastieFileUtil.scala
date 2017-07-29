package com.olegych.scastie.util

import java.nio.file._
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import System.{lineSeparator => nl}

import java.io.{InputStream, OutputStream}

object ScastieFileUtil {
  def slurp(src: Path): Option[String] = {
    if (Files.exists(src)) Some(Files.readAllLines(src).toArray.mkString(nl))
    else None
  }

  def write(dst: Path,
            content: String,
            truncate: Boolean = false,
            append: Boolean = false): Unit = {
    if (!Files.exists(dst)) {
      Files.write(dst, content.getBytes, StandardOpenOption.CREATE_NEW)
      ()
    } else if (truncate) {
      Files.write(dst, content.getBytes, StandardOpenOption.TRUNCATE_EXISTING)
      ()
    } else if (append) {
      Files.write(dst, content.getBytes, StandardOpenOption.APPEND)
      ()
    }
  }

  def usingInputStream[T](path: Path)(f: InputStream => T): T = {
    val stream = Files.newInputStream(path)
    val ret = f(stream)
    stream.close()
    ret
  }

  def usingOutputStream(path: Path)(f: OutputStream => Unit): Unit = {
    val stream = Files.newOutputStream(path)
    val ret = f(stream)
    stream.close()
  }

  def writeRunningPid(): String = {
    val pid = ManagementFactory.getRuntimeMXBean.getName.split("@").head
    val pidFile = Paths.get("RUNNING_PID")
    Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
    sys.addShutdownHook {
      Files.delete(pidFile)
    }
    pid
  }
}
