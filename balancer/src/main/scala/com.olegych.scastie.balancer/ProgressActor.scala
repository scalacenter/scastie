package com.olegych.scastie
package balancer

import api._

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.actor.ActorPublisherMessage.Request

import scala.collection.mutable.{Map => MMap, Queue => MQueue}

case class SubscribeProgress(snippetId: SnippetId)
case class ProgressDone(snippetId: SnippetId)

class ProgressActor extends Actor {
  type ProgressSource = Source[SnippetProgress, NotUsed]

  private val subscribers =
    MMap.empty[SnippetId, (ProgressSource, ActorRef)]

  def receive = {
    case SubscribeProgress(snippetId) => {
      val (source, _) = getOrCreatePublisher(snippetId)
      sender ! source
    }
    case snippetProgress: SnippetProgress => {
      snippetProgress.snippetId.foreach { sid =>
        val (_, publisher) = getOrCreatePublisher(sid)
        publisher ! snippetProgress
      }
    }
    case ProgressDone(snippetId) => {
      subscribers.remove(snippetId)
      ()
    }
  }

  private def getOrCreatePublisher(
      snippetId: SnippetId
  ): (ProgressSource, ActorRef) = {
    def createPublisher() = {
      val ref = context.actorOf(Props(new ProgressForwarder(self)))
      val source = Source.fromPublisher(ActorPublisher[SnippetProgress](ref))
      val sourceAndPublisher = (source, ref)

      subscribers(snippetId) = sourceAndPublisher
      sourceAndPublisher
    }
    subscribers.get(snippetId).getOrElse(createPublisher())
  }
}

class ProgressForwarder(progressActor: ActorRef)
    extends Actor
    with ActorPublisher[SnippetProgress] {

  private var buffer = MQueue.empty[SnippetProgress]
  private var toDeliver = 0L

  def receive = {
    case progress: SnippetProgress => {
      buffer.enqueue(progress)
      deliver(0L)
    }
    case Request(demand) => {
      deliver(demand)
    }
  }

  private def deliver(demand: Long): Unit = {
    toDeliver += demand
    if (toDeliver > 0) {
      val (deliverNow, deliverLater) = buffer.splitAt(toDeliver.toInt)
      buffer = deliverLater
      toDeliver -= deliverNow.size
      deliverNow.foreach { progress =>
        onNext(progress)
        if (progress.done) {
          progress.snippetId.foreach { sid =>
            progressActor ! ProgressDone(sid)
          }
        }
      }
    }
  }
}
