package com.olegych.scastie
package sbt

import api._
import akka.actor.{Actor, ActorSystem, Props, ActorRef, ActorLogging}

import scala.concurrent.duration._

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean,
               readyRef: Option[ActorRef],
               override val reconnectInfo: Option[ReconnectInfo])
    extends Actor
    with ActorLogging
    with ActorReconnecting {

  override def tryConnect(): Unit = {
    reconnectInfo match {
      case Some(info) => {
        import info._

        val sel = context.actorSelection(
          s"akka.tcp://Web@$serverHostname:$serverAkkaPort/user/DispatchActor"
        )

        sel ! SbtRunnerConnect(actorHostname, actorAkkaPort)
      }
      case _ => ()
    }
  }

  private val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  private val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production)),
      name = "SbtRunner"
    )

  override def receive: Receive = reconnectBehavior orElse [Any, Unit] {
    case SbtPing =>
      sender ! SbtPong

    case format: FormatRequest =>
      formatActor.forward(format)

    case task: SbtTask =>
      sbtRunner.forward(task)
  }
}
