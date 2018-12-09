package com.olegych.scastie.balancer

import com.olegych.scastie.api._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.scaladsl.Source
import java.util.concurrent.TimeUnit

import scala.collection.mutable
import scala.concurrent.duration._

import com.olegych.scastie.util.GraphStageForwarder

case object SubscribeStatus

case class SbtLoadBalancerUpdate(newSbtBalancer: SbtBalancer)
case class LoadBalancerInfo(sbtBalancer: SbtBalancer, requester: ActorRef)

case class SetDispatcher(dispatchActor: ActorRef)

object StatusActor {
  def props: Props = Props(new StatusActor)
}
class StatusActor private () extends Actor with ActorLogging {
  private var publishers = mutable.Buffer.empty[ActorRef]

  private var dispatchActor: Option[ActorRef] = None

  override def receive: Receive = {
    case SubscribeStatus => {

      val publisherGraphStage =
        new GraphStageForwarder("StatusActor-GraphStageForwarder", self, None)

      val source =
        Source
          .fromGraph(publisherGraphStage)
          .keepAlive(
            FiniteDuration(1, TimeUnit.SECONDS),
            () => StatusProgress.KeepAlive
          )

      sender ! source
    }

    case (None, publisher: ActorRef) => {
      publishers += publisher
      dispatchActor.foreach(_ ! ReceiveStatus(publisher))
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
