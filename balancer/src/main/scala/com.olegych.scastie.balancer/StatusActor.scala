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

case class SubscribeStatus(ip: Ip)

case class LoadBalancerUpdate(
    newBalancer: LoadBalancer[String, ActorSelection]
)
case class LoadBalancerInfo(balancer: LoadBalancer[String, ActorSelection],
                            requester: ActorRef)
case class SetDispatcher(dispatchActor: ActorRef)

case class NotifyUsers(IPs: Set[Ip], progress: StatusProgress)
case class NotifyAllUsers(progress: StatusProgress)

object StatusActor {
  def props: Props = Props(new StatusActor)
}
class StatusActor private () extends Actor with ActorLogging {
  private var publishers =
    Map[Ip, Vector[ActorRef]]().withDefaultValue(Vector())

  private var dispatchActor: Option[ActorRef] = _
  private def noop(snippetProgress: StatusProgress, noDemmand: Boolean): Unit = {}

  override def receive: Receive = {
    case SubscribeStatus(ip) => {
      log.info(s"statusActor subscribes a client with IP: $ip")

      val publisher =
        context.actorOf(Props(new ActorForwarder[StatusProgress](noop _)))

      publishers = publishers + (ip -> (publisher +: publishers(ip)))

      val source =
        Source
          .fromPublisher(ActorPublisher[StatusProgress](publisher))
          .keepAlive(
            FiniteDuration(1, TimeUnit.SECONDS),
            () => StatusKeepAlive
          )

      sender ! source

      dispatchActor.foreach(_ ! ReceiveStatus(publisher))
    }

    case LoadBalancerUpdate(newBalancer) => {
      publishers.values.flatten.foreach(_ ! convert(newBalancer))
    }

    case LoadBalancerInfo(balancer, requester) => {
      requester ! convert(balancer)
    }

    case SetDispatcher(dispatchActorReference) => {
      dispatchActor = Some(dispatchActorReference)
    }

    case NotifyUsers(ips, progress) => {
      ips.foreach(publishers(_).foreach(_ ! progress))
    }

    case NotifyAllUsers(progress) => {
      publishers.values.flatten.foreach(_ ! progress)
    }
  }

  private def convert(
      newBalancer: LoadBalancer[String, ActorSelection]
  ): StatusProgress = {
    StatusRunnersInfo(
      newBalancer.servers.map(
        server => Runner(server.mailbox.map(_.taskId))
      )
    )
  }
}
