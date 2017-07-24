package com.olegych.scastie
package sbt

import api._
import akka.actor.{
  Actor,
  ActorSystem,
  Props,
  ActorRef,
  ActorLogging,
  Cancellable
}

import scala.concurrent.duration._

import akka.remote.DisassociatedEvent

case class ReconnectInfo(
    serverHostname: String,
    serverAkkaPort: Int,
    runnerHostname: String,
    runnerAkkaPort: Int
)

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean,
               withEnsime: Boolean,
               readyRef: Option[ActorRef],
               reconnectInfo: Option[ReconnectInfo])
    extends Actor
    with ActorLogging {

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    tryConnect()
    super.preStart()
  }

  private var tryReconnectCallback: Option[Cancellable] = None

  def tryConnect(): Unit = {
    reconnectInfo.foreach { info =>
      import info._

      val sel = context.actorSelection(
        s"akka.tcp://SbtRemote@$serverHostname:$serverAkkaPort/user/DispatchActor"
      )

      sel ! SbtRunnerConnect(runnerHostname, runnerAkkaPort)
    }
  }

  private val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  private val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production)),
      name = "SbtRunner"
    )

  private val ensimeActor =
    if (withEnsime) {
      Some(
        context.actorOf(
          Props(new EnsimeActor(context.system, sbtRunner, readyRef)),
          name = "EnsimeActor"
        )
      )
    } else None

  override def receive: Receive = {
    case SbtPing => {
      sender ! SbtPong
    }

    case req: EnsimeTaskRequest => {
      ensimeActor match {
        case Some(ensimeRef) => ensimeRef.forward(req)
        case _               => sender ! EnsimeTaskResponse(None, req.taskId)
      }
    }

    case format: FormatRequest => {
      formatActor.forward(format)
    }

    case task: SbtTask => {
      sbtRunner.forward(task)
    }

    case SbtRunnerConnected => {
      log.info("Connected to server")
      tryReconnectCallback.foreach(_.cancel())
      tryReconnectCallback = None
    }

    case ev: DisassociatedEvent => {
      reconnectInfo.foreach(
        info =>
          if (ev.remoteAddress.host.contains(info.serverHostname) &&
              ev.remoteAddress.port.contains(info.serverAkkaPort) &&
              ev.inbound) {

            log.warning("Disconnected from server")

            import system.dispatcher

            tryReconnectCallback.foreach(_.cancel())
            tryReconnectCallback = Some(
              context.system.scheduler.schedule(0.seconds, 5.seconds) {
                log.info("Reconnecting to server")
                tryConnect()
              }
            )
        }
      )
    }
  }
}
