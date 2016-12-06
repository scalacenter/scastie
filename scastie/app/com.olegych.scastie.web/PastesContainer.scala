package com.olegych.scastie
package web

import api.Paste

import java.nio.file._
import java.util.concurrent.atomic.AtomicLong
import System.{lineSeparator => nl}

class PastesContainer(root: Path) {

  def writePaste(paste: AddPaste): Long = {
    val id = lastId.incrementAndGet()

    if (Files.exists(pasteDir(id)))
      throw new Exception(
        s"trying to write paste to existing folder: ${pasteDir(id).toString}")

    val codeFilePath = codeFile(id)
    codeFilePath.getParent.toFile.mkdirs()
    write(codeFilePath, paste.code)
    write(sbtConfigFile(id), paste.sbtConfig)

    id
  }

  def readPaste(id: Long): Option[Paste] = {
    if (Files.exists(pasteDir(id))) {
      (read(codeFile(id)), read(sbtConfigFile(id))) match {
        case (Some(code), Some(sbtConfig)) => Some(Paste(id, code, sbtConfig))
        case _                             => None
      }
    } else None
  }

  private val lastId = {
    val last =
      if (Files.exists(root)) {
        import scala.collection.JavaConverters._
        val PasteFormat = "paste(\\d+)".r
        val ds          = Files.newDirectoryStream(root)
        val lastPasteNumber =
          ds.asScala
            .map(_.getFileName.toString)
            .collect {
              case PasteFormat(id) => id.toLong
            }
            .max
        ds.close()
        lastPasteNumber
      } else 0L

    new AtomicLong(last)
  }

  private def pasteDir(id: Long): Path = root.resolve("paste" + id)
  private def codeFile(id: Long) =
    pasteDir(id).resolve(Paths.get("main.scala"))
  private def sbtConfigFile(id: Long) =
    pasteDir(id).resolve(Paths.get("config.sbt"))
}
