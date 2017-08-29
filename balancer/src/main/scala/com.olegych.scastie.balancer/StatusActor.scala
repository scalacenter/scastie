package com.olegych.scastie.balancer

import java.net.InetAddress

import com.olegych.scastie.api._
import com.olegych.scastie.util.ActorForwarder

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.RemoteAddress

import scala.collection.mutable
import scala.concurrent.duration._

case object SubscribeStatus

case class SbtLoadBalancerUpdate(newSbtBalancer: SbtBalancer)
case class EnsimeLoadBalancerUpdate(newEnsimeBalancer: EnsimeBalancer)

case class LoadBalancerInfo(
    sbtBalancer: SbtBalancer,
    ensimeBalancer: EnsimeBalancer,
    requester: ActorRef
)

case class SetDispatcher(dispatchActor: ActorRef)

object StatusActor {
  def props: Props = Props(new StatusActor)
}
class StatusActor private () extends Actor with ActorLogging {
  private var publishers = mutable.Buffer.empty[ActorRef]

  private var dispatchActor: Option[ActorRef] = _

  override def receive: Receive = {
    case SubscribeStatus => {

      def noop(snippetProgress: StatusProgress, noDemmand: Boolean): Unit = {}

      val publisher =
        context.actorOf(Props(new ActorForwarder[StatusProgress](noop _)))

      publishers += publisher

      val source =
        Source
          .fromPublisher(ActorPublisher[StatusProgress](publisher))
          .keepAlive(
            FiniteDuration(1, TimeUnit.SECONDS),
            () => StatusProgress.KeepAlive
          )

      sender ! source

      dispatchActor.foreach(_ ! ReceiveStatus(publisher))
    }

    case SbtLoadBalancerUpdate(newSbtBalancer) => {
      publishers.foreach(_ ! convertSbt(newSbtBalancer))
    }

    case EnsimeLoadBalancerUpdate(newEnsimeBalancer) => {
      publishers.foreach(_ ! convertEnsime(newEnsimeBalancer))
    }

    case LoadBalancerInfo(sbtBalancer, ensimeBalancer, requester) => {
      requester ! convertSbt(sbtBalancer)
      requester ! convertEnsime(ensimeBalancer)
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
            config = Some(server.lastConfig),
            tasks = server.mailbox.map(_.taskId),
            sbtState = server.state
        )
      )
    )
  }

  private def convertEnsime(newEnsimeBalancer: EnsimeBalancer): StatusProgress = {
    StatusProgress.Ensime(
      newEnsimeBalancer.servers.map(
        server =>
          EnsimeRunnerState(
            config = Some(server.lastConfig),
            tasks = server.mailbox.map(_.taskId),
            serverState = server.state
        )
      )
    )
  }
}
