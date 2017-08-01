package com.olegych.scastie.balancer

import com.olegych.scastie.util.ActorForwarder
import com.olegych.scastie.proto._

import akka.NotUsed
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source

import scala.collection.mutable.{Map => MMap, Queue => MQueue}

case class SubscribeProgress(snippetId: SnippetId)

class ProgressActor extends Actor {
  type ProgressSource = Source[SnippetProgress, NotUsed]

  private val subscribers =
    MMap.empty[SnippetId, (ProgressSource, ActorRef)]

  override def receive: Receive = {
    case SubscribeProgress(snippetId) =>
      val (source, _) = getOrCreatePublisher(snippetId)
      sender ! source

    case snippetProgress: SnippetProgress => {
      snippetProgress.snippetId.foreach { snippetId =>
        val (_, publisher) = getOrCreatePublisher(snippetId)
        publisher ! snippetProgress
      }

      if (snippetProgress.done) {
        snippetProgress.snippetId.foreach { snippetId =>
          subscribers.remove(snippetId)
        }
      }
    }
  }

  private def getOrCreatePublisher(
      snippetId: SnippetId
  ): (ProgressSource, ActorRef) = {
    def createPublisher() = {
      val ref = context.actorOf(Props(new ActorForwarder[SnippetProgress]()))
      val source = Source.fromPublisher(ActorPublisher[SnippetProgress](ref))
      val sourceAndPublisher = (source, ref)

      subscribers(snippetId) = sourceAndPublisher
      sourceAndPublisher
    }
    subscribers.getOrElse(snippetId, createPublisher())
  }
}
