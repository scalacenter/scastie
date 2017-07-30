package com.olegych.scastie.util

import akka.actor.{Actor, ActorRef}
import akka.stream.actor.ActorPublisher
import scala.collection.mutable.{Queue => MQueue}

import akka.stream.actor.ActorPublisherMessage.Request
import scala.reflect.ClassTag

class ActorForwarder[T: ClassTag]()
    extends Actor
    with ActorPublisher[T] {

  private var buffer = MQueue.empty[T]
  private var toDeliver = 0L

  override def receive: Receive = {
    case msg: T =>
      buffer.enqueue(msg)
      deliver(0L)

    case Request(demand) =>
      deliver(demand)
  }

  private def deliver(demand: Long): Unit = {
    toDeliver += demand
    if (toDeliver > 0) {
      val (deliverNow, deliverLater) = buffer.splitAt(toDeliver.toInt)
      buffer = deliverLater
      toDeliver -= deliverNow.size
      deliverNow.foreach { msg =>
        onNext(msg)
      }
    }
  }
}