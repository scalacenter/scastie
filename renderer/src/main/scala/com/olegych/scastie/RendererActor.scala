package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import java.io.File

/**
  */
class RendererActor(pastesContainer: PastesContainer) extends Actor with ActorLogging {
  val sbtDir = pastesContainer.child(util.Random.alphanumeric.take(10).mkString)

  var sbt: Option[Sbt] = None

  override def preStart() {
    log.info("creating paste sbt project")
    val l = new RendererTemplate(sbtDir.sbtRoot).create
    log.info(l)
    log.info("starting sbt")
    sbt = Option(new Sbt(sbtDir.sbtRoot))
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  protected def receive = LoggingReceive {
    case paste: String => {
      sbt match {
        case Some(sbt) =>
          import scalax.io.Resource._
          val pasteFile = fromFile(sbtDir.pasteFile)
          pasteFile.truncate(0)
          pasteFile.write(paste)
          val result = sbt.process("compile")
          val sxrSource = fromFile(sbtDir.sxrSource).slurpString
          sender ! (result + "\n" + cleanSource(sxrSource))
        case _ => sender ! "sbt not started"
      }
    }
  }

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}

case class PastesContainer(sbtRoot: java.io.File) {
  def child(id: String) = copy(sbtRoot = new File(sbtRoot, id))
  def pasteFile = new File(sbtRoot, "src/main/scala/test.scala")
  def sxrSource = new File(sbtRoot, "target/scala-2.9.2/classes.sxr/test.scala.html")
}