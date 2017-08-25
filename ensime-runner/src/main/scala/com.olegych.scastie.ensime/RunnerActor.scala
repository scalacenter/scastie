package com.olegych.scastie.ensime

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSelection,
  ActorSystem,
  PoisonPill,
  Props
}
import akka.remote.DisassociatedEvent
import com.olegych.scastie.api.{
  EnsimeRunnerConnect,
  EnsimeTaskRequest,
  EnsimeTaskResponse,
  SbtRunnerConnect
}
import com.olegych.scastie.{ActorReconnecting, ReconnectInfo}

import scala.concurrent.duration._

class RunnerActor(system: ActorSystem,
                  runTimeout: FiniteDuration,
                  production: Boolean,
                  override val reconnectInfo: Option[ReconnectInfo])
    extends Actor
    with ActorLogging
    with ActorReconnecting {

  private var dispatchActor: Option[ActorSelection] = None
  private var ensimeActor: Option[ActorRef] = None

  override def tryConnect(): Unit = {
    reconnectInfo match {
      case Some(info) => {
        import info._

        val sel = context.actorSelection(
          s"akka.tcp://Web@$serverHostname:$serverAkkaPort/user/DispatchActor"
        )

        dispatchActor = Some(sel)

        sel ! EnsimeRunnerConnect(actorHostname, actorAkkaPort)
      }
      case _ => ()
    }
  }

  override def onConnected(): Unit = {
    import system.dispatcher

    if (ensimeActor.isEmpty) {
      dispatchActor.get
        .resolveOne(1.minute)
        .foreach(
          ref =>
            ensimeActor = Some(
              context.actorOf(
                Props(new EnsimeActor(context.system, ref)),
                name = "EnsimeActor"
              )
          )
        )

    }
  }

  override def onDisconnected(): Unit = {
    if (ensimeActor.isDefined) {
      log.info("Stopping ensime actor")
      ensimeActor.get ! PoisonPill
      ensimeActor = None
    }
  }

  override def receive: Receive = reconnectBehavior orElse [Any, Unit] {
    case req: EnsimeTaskRequest =>
      ensimeActor match {
        case Some(ensimeRef) => ensimeRef.forward(req)
        case _               => sender ! EnsimeTaskResponse(None, req.taskId)
      }
  }
}
