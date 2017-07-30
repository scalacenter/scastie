package com.olegych.scastie.balancer

import com.olegych.scastie.util.ActorForwarder
import com.olegych.scastie.api._
import com.olegych.scastie.proto._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import scala.collection.mutable

case object SubscribeStatus

case class LoadBalancerUpdate(
    newBalancer: LoadBalancer[String, ActorSelection]
)

case class LoadBalancerInfo(
  balancer: LoadBalancer[String, ActorSelection],
  originalSender: ActorRef
)

case class SetDispatcher(
  dispatchActor: ActorRef
)

object StatusActor {
  def props: Props = Props(new StatusActor)
}
class StatusActor private () extends Actor with ActorLogging {
  private val publishers = mutable.Buffer.empty[ActorRef]

  var dispatchActor: Option[ActorRef] = _

  override def receive: Receive = {
    case SubscribeStatus =>
      val publisher = context.actorOf(Props(new ActorForwarder[SnippetProgress]()))

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

    case LoadBalancerUpdate(newBalancer) =>
      publishers.foreach(_ ! convert(newBalancer))

    case LoadBalancerInfo(balancer, originalSender) =>
      originalSender ! convert(balancer)

    case SetDispatcher(dispatchActorReference) =>
      dispatchActor = Some(dispatchActorReference)
  }

  private def convert(
      newBalancer: LoadBalancer[String, ActorSelection]
  ): StatusProgress = {
    StatusInfo(
      newBalancer.servers.map(
        server => Runner(server.mailbox.map(_.taskId))
      )
    )
  }
}
