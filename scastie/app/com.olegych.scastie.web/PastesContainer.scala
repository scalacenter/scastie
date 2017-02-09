package com.olegych.scastie
package web

import api._

import upickle.default.{write => uwrite, read => uread}

import java.nio.file._
import System.{lineSeparator => nl}

class PastesContainer(root: Path) {

  def writePaste(inputs: Inputs): Int = {
    val id = lastId
    lastId += 1
    write(inputsFile(id), uwrite(inputs))
    id
  }

  def appendOutput(progress: PasteProgress): Unit = {
    write(outputFile(progress.id), uwrite(progress) + nl, append = true)
  }

  def readPaste(id: Int): Option[Inputs] = {
    if (Files.exists(inputsFile(id))) {
      read(inputsFile(id)).map(content => uread[Inputs](content))
    } else None
  }

  def readOutput(id: Int): Option[List[PasteProgress]] = {
    if (Files.exists(outputFile(id)))
      read(outputFile(id)).map(
        _.lines
          .filter(_.nonEmpty)
          .map(line => uread[PasteProgress](line))
          .toList)
    else None
  }

  private val json = ".json"
  private val input = "input"
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
            .toInt
        )
        .max
    } catch {
      case util.control.NonFatal(e) => 0
    } finally {
      ds.close()
    }
  }

  private def inputsFile(id: Int) =
    root.resolve(Paths.get(input + id + json))

  private def outputFile(id: Int) =
    root.resolve(Paths.get(output + id + json))
}
