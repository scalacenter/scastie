package com.olegych.scastie.util

import java.nio.file._

object ScastieFileUtil {
  def slurp(src: Path): Option[String] = {
    if (Files.exists(src)) Some(Files.readAllLines(src).toArray.mkString("\n"))
    else None
  }

  def write(dst: Path, content: String, truncate: Boolean = false, append: Boolean = false): Unit = {
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
}
