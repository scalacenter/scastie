package com.olegych.scastie
package balancer

import api._

import akka.NotUsed
import akka.actor.{ActorLogging, Actor, ActorRef, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.actor.ActorPublisherMessage.Request

import scala.collection.mutable.{Map => MMap, Queue => MQueue}

case class SubscribeProgress(id: String)
case class ProgressDone(id: String)

class ProgressActor extends Actor with ActorLogging {
  type ProgressSource = Source[PasteProgress, NotUsed]

  private val subscribers =
    MMap.empty[String, (ProgressSource, ActorRef)]

  def receive = {
    case SubscribeProgress(id) => {
      val (source, _) = getOrCreatePublisher(id)
      sender ! source
    }
    case pasteProgress: PasteProgress => {
      val id = pasteProgress.id.toString
      val (_, publisher) = getOrCreatePublisher(id)
      publisher ! pasteProgress
    }
    case ProgressDone(id) => {
      subscribers.remove(id)
      ()
    }
  }

  private def getOrCreatePublisher(id: String): (ProgressSource, ActorRef) = {
    def createPublisher() = {
      val ref = context.actorOf(Props(new ProgressForwarder(self)))       
      val source = Source.fromPublisher(ActorPublisher[PasteProgress](ref))
      val sourceAndPublisher = (source, ref)

      subscribers(id) = sourceAndPublisher
      sourceAndPublisher
    }
    subscribers.get(id).getOrElse(createPublisher())
  }
}

class ProgressForwarder(progressActor: ActorRef) extends Actor with ActorPublisher[PasteProgress] {
  var buffer = MQueue.empty[PasteProgress]

  def receive = {
    case progress: PasteProgress => {
      buffer.enqueue(progress)
      deliver()
      if(progress.done) {
        progressActor ! ProgressDone(progress.id.toString)
      }
    }
    case Request(_) => {
      deliver()
    }
  }

  private def deliver(): Unit = {
    if(totalDemand > 0) {
      buffer.foreach(onNext)
      buffer.clear()
    }
  }
}
