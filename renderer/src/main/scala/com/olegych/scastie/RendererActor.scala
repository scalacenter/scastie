package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import java.io.File
import com.olegych.scastie.PastesActor.Paste
import java.util.concurrent.atomic.AtomicLong

/**
  */
class RendererActor(pastesContainer: PastesContainer) extends Actor with ActorLogging {
  val sbtDir = pastesContainer.renderer(util.Random.alphanumeric.take(10).mkString)

  var sbt: Option[Sbt] = None

  override def preStart() {
    log.info("creating paste sbt project")
    val l = new RendererTemplate(sbtDir.root).create
    log.info(l)
    log.info("starting sbt")
    sbt = Option(new Sbt(sbtDir.root))
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  protected def receive = LoggingReceive {
    case paste@Paste(id, content, output) => {
      sbt match {
        case Some(sbt) =>
          import scalax.io.Resource._
          val pasteFile = fromFile(sbtDir.pasteFile)
          pasteFile.truncate(0)
          pasteFile.write(content)
          val result = sbt.process("compile")
          val sxrSource = fromFile(sbtDir.sxrSource).slurpString
          sender ! paste.copy(content = cleanSource(sxrSource), output = result)
      }
    }
  }

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}

case class PastesContainer(root: java.io.File) {
  val PasteFormat = "paste(\\d+)".r
  lazy val lastPasteId = new AtomicLong(Option(root.listFiles()).getOrElse(Array()).collect {
    case PasteFormat(id) => id.toLong
  }.sorted.lastOption.getOrElse(0L))

  def renderer(id: String) = child("renderer" + id)
  def paste(id: Long) = child("paste%20d".format(id).replaceAll(" ", "0"))
  private def child(id: String) = copy(root = new File(root, id))
  def pasteFile = new File(root, "src/main/scala/test.scala")
  def sxrSource = new File(root, "target/scala-2.9.2/classes.sxr/test.scala.html")
}