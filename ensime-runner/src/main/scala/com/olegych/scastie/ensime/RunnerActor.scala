package com.olegych.scastie.ensime

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, PoisonPill, Props}
import akka.remote.DisassociatedEvent
import com.olegych.scastie.api.{EnsimeRunnerConnect, EnsimeTaskRequest, EnsimeTaskResponse, SbtRunnerConnect}
import com.olegych.scastie.{ActorReconnecting, ReconnectInfo}

import scala.concurrent.duration.FiniteDuration

class RunnerActor(system: ActorSystem,
                  runTimeout: FiniteDuration,
                  production: Boolean,
                  override val reconnectInfo: ReconnectInfo)
    extends Actor
    with ActorLogging with ActorReconnecting {

  private var dispatchActor: Option[ActorSelection] = None
  private var ensimeActor: Option[ActorRef] = None

  override def tryConnect(): Unit = {
    import reconnectInfo._

    dispatchActor = Some(context.actorSelection(
      s"akka.tcp://Web@$serverHostname:$serverAkkaPort/user/DispatchActor"
    ))

    dispatchActor.foreach(_ ! EnsimeRunnerConnect(actorHostname, actorAkkaPort))
  }

  override def onConnected(): Unit = {
    if (ensimeActor.isEmpty) {
      ensimeActor = Some(context.actorOf(
        Props(new EnsimeActor(context.system, dispatchActor.get)),
        name = "EnsimeActor"
      ))
    }
  }

  override def onDisconnected(): Unit = {
    if (ensimeActor.isDefined) {
      log.info("Stopping ensime actor")
      ensimeActor.get ! PoisonPill
      ensimeActor = None
    }
  }

  override def receive: Receive = reconnectBehavior orElse[Any, Unit] {
    case req: EnsimeTaskRequest =>
      ensimeActor match {
        case Some(ensimeRef) => ensimeRef.forward(req)
        case _               => sender ! EnsimeTaskResponse(None, req.taskId)
      }
  }
}
