package com.olegych.scastie.balancer

import akka.stream.actor.ActorPublisherMessage.Request
import com.olegych.scastie.api.StatusProgress
import akka.actor.{Actor, Props}
import akka.stream.actor.ActorPublisher

import scala.collection.mutable.{Queue => MQueue}

object StatusForwarder {
  def props: Props = Props(new StatusForwarder())
}

class StatusForwarder private ()
    extends Actor
    with ActorPublisher[StatusProgress] {
  private val buffer = MQueue.empty[StatusProgress]
  val maxSize = 10

  override def receive: Receive = {
    case progress: StatusProgress =>
      if (buffer.size >= maxSize) {
        buffer.dequeue()
      }
      buffer.enqueue(progress)
      deliver()
    case _: Request =>
      deliver()
  }

  private def deliver(): Unit = {
    if (totalDemand > 0) {
      buffer.foreach { progress =>
        onNext(progress)
      }
      buffer.clear()
    }
  }
}
