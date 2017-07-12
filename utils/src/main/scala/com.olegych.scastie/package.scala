package com.olegych

import java.nio.file._
import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import System.{lineSeparator => nl}

package object scastie {

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

  def writeRunningPid(): String = {
    val pid = ManagementFactory.getRuntimeMXBean.getName().split("@").head
    val pidFile = Paths.get("RUNNING_PID")
    Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
    sys.addShutdownHook {
      Files.delete(pidFile)
    }
    pid
  }
}
