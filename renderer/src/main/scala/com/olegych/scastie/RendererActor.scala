package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive

/**
  */
class RendererActor extends Actor with ActorLogging {
  var sbt: Sbt = _

  override def preStart() {
    log.info("starting sbt")
    sbt = new Sbt("renderer-template")
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.close()
  }

  protected def receive = LoggingReceive {
    case paste: String => {
      log.info("paste " + paste)
      val result = sbt.process(paste)
      log.info("result " + result)
      sender ! result
    }
  }
}
