package com.olegych.scastie
package web

import api._

import upickle.default.{write => uwrite, read => uread}

import java.nio.file._
import java.util.concurrent.atomic.AtomicLong
import System.{lineSeparator => nl}

class PastesContainer(root: Path) {

  def writePaste(inputs: Inputs): Long = {
    val id = lastId
    lastId += 1
    write(inputsFile(id), uwrite(inputs))
    id
  }

  def appendOutput(progress: PasteProgress): Unit = {
    write(outputFile(progress.id), uwrite(progress) + nl, append = true)
  }

  def readPaste(id: Long): Option[Inputs] = {
    if (Files.exists(inputsFile(id))) {
      read(inputsFile(id)).map(content => uread[Inputs](content))
    } else None
  }

  def readOutput(id: Long): Option[List[PasteProgress]] = {
    if (Files.exists(outputFile(id)))
      read(outputFile(id)).map(
        _.lines
          .filter(_.nonEmpty)
          .map(line => uread[PasteProgress](line))
          .toList)
    else None
  }

  private val json   = ".json"
  private val input  = "input"
  private val output = "output"

  private var lastId = {
    import scala.collection.JavaConverters._

    Files.createDirectories(root)
    val ds = Files.newDirectoryStream(root)

    try {
      ds.asScala
        .map(
          _.getFileName.toString
            .stripPrefix(input)
            .stripPrefix(output)
            .stripSuffix(json)
            .toLong
        )
        .max
    } catch {
      case util.control.NonFatal(e) => 0L
    } finally {
      ds.close()
    }
  }

  private def inputsFile(id: Long) =
    root.resolve(Paths.get(input + id + json))

  private def outputFile(id: Long) =
    root.resolve(Paths.get(output + id + json))
}
