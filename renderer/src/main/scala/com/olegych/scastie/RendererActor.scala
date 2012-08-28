package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}

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

  protected def receive = {
    case paste => {
      sender ! sbt.process("hello")
    }
  }
}
