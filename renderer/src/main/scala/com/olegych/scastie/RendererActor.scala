package com.olegych.scastie

import akka.actor.Actor

/**
 */
class RendererActor extends Actor {
  protected def receive = {case paste => {
    import scala.sys.process._

    def sbt(command: String): String = {
      (if (org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS) "xsbt.cmd " else "./xsbt.sh ") + command
    }

    val res = sbt("hello").lines_!.toList.mkString("\n")
    sender ! (res + paste)
  }}
}
