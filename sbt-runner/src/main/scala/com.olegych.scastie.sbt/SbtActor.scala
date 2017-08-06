package com.olegych.scastie
package sbt

import api._
import akka.actor.{
  Actor,
  ActorSystem,
  Props,
  ActorRef,
  ActorLogging
}

import scala.concurrent.duration._

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean,
               readyRef: Option[ActorRef],
               override val reconnectInfo: ReconnectInfo)
    extends Actor
    with ActorLogging with ActorReconnecting {

  override def tryConnect(): Unit = {
    import reconnectInfo._

    val sel = context.actorSelection(
      s"akka.tcp://Web@$serverHostname:$serverAkkaPort/user/DispatchActor"
    )

    sel ! SbtRunnerConnect(actorHostname, actorAkkaPort)
  }

  private val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  private val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production)),
      name = "SbtRunner"
    )

  override def receive: Receive = reconnectBehavior orElse[Any, Unit] {
    case SbtPing =>
      sender ! SbtPong

    case format: FormatRequest =>
      formatActor.forward(format)

    case task: SbtTask =>
      sbtRunner.forward(task)

    case req: CreateEnsimeConfigRequest =>
      sbtRunner.forward(req)
  }
}
