package com.olegych.scastie
package balancer

import api._

import util.ActorForwarder

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

  override def receive: Receive = {
    case SubscribeProgress(snippetId) => {
      val (source, _) = getOrCreatePublisher(snippetId, self)
      sender ! source
    }

    case snippetProgress: SnippetProgress => {
      snippetProgress.snippetId.foreach { snippetId =>
        val (_, publisher) = getOrCreatePublisher(snippetId, self)
        publisher ! snippetProgress
      }
    }

    case ProgressDone(snippetId) => {
      subscribers.remove(snippetId)
      ()
    }
  }

  private def getOrCreatePublisher(
      snippetId: SnippetId,
      self: ActorRef
  ): (ProgressSource, ActorRef) = {

    def doneCallback(snippetProgress: SnippetProgress,
                     noDemmand: Boolean): Unit = {
      if (snippetProgress.done && noDemmand) {
        snippetProgress.snippetId.foreach { snippetId =>
          self ! ProgressDone
        }
      }
    }

    def createPublisher() = {
      val ref = context.actorOf(
        Props(
          new ActorForwarder[SnippetProgress](doneCallback _)
        )
      )
      val source = Source.fromPublisher(ActorPublisher[SnippetProgress](ref))
      val sourceAndPublisher = (source, ref)

      sourceAndPublisher
    }

    subscribers.getOrElseUpdate(snippetId, createPublisher())
  }
}
