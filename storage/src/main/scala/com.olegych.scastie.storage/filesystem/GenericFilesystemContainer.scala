package com.olegych.scastie.storage.filesystem

import java.nio.file._

trait GenericFilesystemContainer {

  def write(path: Path, content: String): Unit = {
    Files.write(path, content.getBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def append(path: Path, content: String): Unit = {
    Files.write(path, content.getBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }

  def slurp(src: Path): Option[String] = {
    if (Files.exists(src)) Some(new String(Files.readAllBytes(src)))
    else None
  }
}
