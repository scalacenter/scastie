package com.olegych

import java.nio.file._
import util.Properties
import System.{lineSeparator => nl}

package object scastie {

  def read(src: Path): Option[String] = {
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

  def writeRunningPid() {
    java.lang.management.ManagementFactory.getRuntimeMXBean.getName
      .split('@')
      .headOption
      .foreach { pid =>
        val pidFile = Paths.get(Properties.userDir, "RUNNING_PID")
        println(s"Runner PID: $pid")
        Files.write(pidFile, pid.getBytes)
        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run: Unit = {
            Files.delete(pidFile)
            ()
          }
        })
      }
  }
}
