package com.olegych

import java.nio.file._
import attribute.BasicFileAttributes

import System.{lineSeparator => nl}

package object scastie {
  def read(src: Path): Option[String] = {
    if (Files.exists(src)) Some(Files.readAllLines(src).toArray.mkString(nl))
    else None
  }

  def write(dst: Path, content: String, truncate: Boolean = false): Unit = {
    if (!Files.exists(dst)) {
      Files.write(dst, content.getBytes, StandardOpenOption.CREATE_NEW)
      ()
    } else if (truncate) {
      Files.write(dst, content.getBytes, StandardOpenOption.TRUNCATE_EXISTING)
      ()
    }
  }

  def copyDir(src: Path, dst: Path): Unit = {
    Files.walkFileTree(src, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(
          dir: Path,
          attrs: BasicFileAttributes): FileVisitResult = {
        Files.createDirectories(dst.resolve(src.relativize(dir)))
        FileVisitResult.CONTINUE
      }
      override def visitFile(file: Path,
                             attrs: BasicFileAttributes): FileVisitResult = {
        Files.copy(file, dst.resolve(src.relativize(file)))
        FileVisitResult.CONTINUE
      }
    })

    ()
  }
}
