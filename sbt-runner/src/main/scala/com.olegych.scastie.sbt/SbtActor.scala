package com.olegych.scastie.sbt

import com.olegych.scastie.api._
import com.olegych.scastie.util._
import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSelection, ActorSystem, Props}

import scala.concurrent.duration._

case object SbtActorReady

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               sbtReloadTimeout: FiniteDuration,
               isProduction: Boolean,
               readyRef: Option[ActorRef],
               override val reconnectInfo: Option[ReconnectInfo])
    extends Actor
    with ActorLogging
    with ActorReconnecting {

  def balancer(context: ActorContext, info: ReconnectInfo): ActorSelection = {
    import info._
    context.actorSelection(
      s"akka.tcp://Web@$serverHostname:$serverAkkaPort/user/DispatchActor"
    )
  }

  override def tryConnect(context: ActorContext): Unit = {
    if (isProduction) {
      reconnectInfo.foreach { info =>
        import info._
        balancer(context, info) ! SbtRunnerConnect(actorHostname, actorAkkaPort)
      }
    }
  }

  override def preStart(): Unit = {
    log.info("*** SbtRunner preStart ***")

    readyRef.foreach(_ ! SbtActorReady)
    super.preStart()
  }

  override def postStop(): Unit = {
    log.info("*** SbtRunner postStop ***")

    super.postStop()
  }

  private val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  private val sbtRunner =
    context.actorOf(
      Props(
        new SbtProcess(
          runTimeout,
          sbtReloadTimeout,
          isProduction,
          javaOptions = Seq("-Xms512m", "-Xmx1g")
        )
      ),
      name = "SbtRunner"
    )

  override def receive: Receive = reconnectBehavior orElse [Any, Unit] {
    case SbtPing => {
      sender ! SbtPong
    }

    case format: FormatRequest => {
      formatActor.forward(format)
    }

    case task: SbtTask => {
      sbtRunner.forward(task)
    }

    case SbtUp => {
      log.info("SbtUp")
      reconnectInfo.foreach { info =>
        log.info("SbtUp sent")
        balancer(context, info) ! SbtUp
      }
    }

    case replay: Replay => {
      log.info("Replay")
      reconnectInfo.foreach { info =>
        log.info("Replay sent")
        balancer(context, info) ! replay
      }
    }
  }
}
