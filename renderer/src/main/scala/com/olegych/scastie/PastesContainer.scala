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
  def pasteFile = new ExtendedFile("src/main/scala/test.scala")
  def pasteSettingsFile = new ExtendedFile("test.sbt")
  def outputFile = new ExtendedFile("src/main/scala/output.txt")
  def uidFile = new ExtendedFile("src/main/scala/uid.txt")
  def sxrSource = new ExtendedFile("target/classes.sxr/src/main/scala/test.scala.html")

  class ExtendedFile(path: String) {
    val file = new File(root, path)

    import scalax.io.Resource._

    def read: Option[String] = {
      Option(fromFile(file).string).filter(!_.isEmpty)
    }

    def write(content: Option[String], truncate: Boolean = true) = {
      file.getParentFile.mkdirs()
      content.map { content =>
        val writer = fromFile(file)
        if (truncate) {
          writer.truncate(0)
        } else {
          writer.write("\n")
        }
        writer.write(content)
      }
    }

    def exists = file.exists()

    def delete() = file.delete()
  }

}
