package com.olegych.scastie.balancer

import com.olegych.scastie.api._

import StatusProgress.KeepAlive
import akka.{actor => classic}
import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl.Source
import java.util.concurrent.TimeUnit

import scala.collection.mutable
import scala.concurrent.duration._

import com.olegych.scastie.util.GraphStageForwarder
import StatusActor._

case class SubscribeStatus(replyTo: ActorRef[Source[KeepAlive.type, NotUsed]]) extends Message

case class SbtLoadBalancerUpdate(newSbtBalancer: SbtBalancer) extends Message
case class LoadBalancerInfo(sbtBalancer: SbtBalancer, requester: ActorRef[StatusProgress]) extends Message

case class SetDispatcher(dispatchActor: ActorRef[ReceiveStatus]) extends Message

object StatusActor {
  sealed trait Message

  private case class GraphStageForwarderMsg(
    graphStageForwarderActor: ActorRef[StatusProgress]
  ) extends Message

  def apply(): Behavior[Message] =
    Behaviors.supervise {
      Behaviors.setup(new StatusActor(_))
    }.onFailure(SupervisorStrategy.resume)
}

class StatusActor private (ctx: ActorContext[Message]) extends AbstractBehavior[Message](ctx) {
  import context.self

  private val publishers = mutable.Buffer.empty[ActorRef[StatusProgress]]

  private var dispatchActor: Option[ActorRef[ReceiveStatus]] = None

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case SubscribeStatus(replyTo) =>
        val publisherGraphStage =
          new GraphStageForwarder(
            "StatusActor-GraphStageForwarder",
            //send from com.olegych.scastie.util.GraphStageLogicForwarder.preStart
            context.messageAdapter[(None.type, classic.ActorRef)] {
              case (_, publisher) => GraphStageForwarderMsg(publisher.toTyped[StatusProgress])
            }.toClassic,
            None
          )

        val source =
          Source
            .fromGraph(publisherGraphStage)
            .keepAlive(
              FiniteDuration(1, TimeUnit.SECONDS),
              () => StatusProgress.KeepAlive
            )

        replyTo ! source

      case GraphStageForwarderMsg(publisher) => {
        publishers += publisher
        dispatchActor.foreach(_ ! ReceiveStatus(self, publisher))
      }

      case SbtLoadBalancerUpdate(newSbtBalancer) => {
        publishers.foreach(_ ! convertSbt(newSbtBalancer))
      }

      case LoadBalancerInfo(sbtBalancer, requester) => {
        requester ! convertSbt(sbtBalancer)
      }

      case SetDispatcher(dispatchActorReference) => {
        dispatchActor = Some(dispatchActorReference)
      }
    }

    this
  }

  private def convertSbt(newSbtBalancer: SbtBalancer): StatusProgress = {
    StatusProgress.Sbt(
      newSbtBalancer.servers.map(
        server =>
          SbtRunnerState(
            config = server.lastConfig,
            tasks = server.mailbox.map(_.taskId),
            sbtState = server.state
        )
      )
    )
  }
}
