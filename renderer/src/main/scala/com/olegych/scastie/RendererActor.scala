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
    val l = new RendererTemplate(sbtDir.file).create
    log.info(l)
    log.info("starting sbt")
    sbt = Option(new Sbt(sbtDir.file))
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  protected def receive = LoggingReceive {
    case paste: String => {
      sbt match {
        case Some(sbt) =>
          log.info("paste " + paste)
          val result = sbt.process(paste)
          log.info("result " + result)
          sender ! result
      }
    }
  }
}
case class PastesContainer(file:java.io.File) {
  def child(id:String) = copy(file = new File(file, id))
}