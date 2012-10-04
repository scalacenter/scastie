package com.olegych.scastie

import java.util.concurrent.atomic.AtomicLong
import java.io.File

//todo split into worker and main containers to remove confusion (or replace with proper db)
case class PastesContainer(root: java.io.File) {
  val PasteFormat = "paste(\\d+)".r
  lazy val lastPasteId = new AtomicLong(Option(root.listFiles()).getOrElse(Array()).map(_.getName).collect {
    case PasteFormat(id) => id.toLong
  }.sorted.lastOption.getOrElse(0L))

  def renderer(id: String) = child("renderer" + id)
  def paste(id: Long) = child("paste%20d".format(id).replaceAll(" ", "0"))
  private def child(id: String) = copy(root = new File(root, id))
  def pasteFile = new File(root, "src/main/scala/test.scala")
  def outputFile = new File(root, "src/main/scala/output.txt")
  def sxrSource = new File(root, "target/scala-2.9.2/classes.sxr/test.scala.html")

  def writeFile(file: File, content: Option[String], truncate: Boolean = true) {
    content.map { content =>
      import scalax.io.Resource._
      val writer = fromFile(file)
      if (truncate) {
        writer.truncate(0)
      } else {
        writer.write("\n")
      }
      writer.write(content)
    }
  }
}
