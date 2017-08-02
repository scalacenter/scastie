package com.olegych.scastie.util

import akka.actor.{Actor, ActorRef}
import akka.stream.actor.ActorPublisher
import scala.collection.mutable.{Queue => MQueue}

import akka.stream.actor.ActorPublisherMessage.Request
import scala.reflect.ClassTag

class ActorForwarder[T: ClassTag](
    callback: (T, Boolean) => Unit
) extends Actor
    with ActorPublisher[T] {

  private var buffer = MQueue.empty[T]

  override def receive: Receive = {
    case msg: T => {
      buffer.enqueue(msg)
      deliver()
    }

    case _: Request => {
      deliver()
    }
  }

  private def deliver(): Unit = {
    if (totalDemand > 0) {
      val (deliverNow, deliverLater) = buffer.splitAt(totalDemand.toInt)
      buffer = deliverLater
      val noDemmand = totalDemand == 0L
      deliverNow.foreach { msg =>
        onNext(msg)
        callback(msg, noDemmand)
      }
    }
  }
}
