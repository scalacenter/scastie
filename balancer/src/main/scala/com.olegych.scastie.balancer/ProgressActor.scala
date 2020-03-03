package com.olegych.scastie
package balancer

import akka.NotUsed
import akka.actor.{Actor, ActorRef}
import akka.stream.scaladsl.Source
import com.olegych.scastie.api._
import com.olegych.scastie.util.GraphStageForwarder

import scala.collection.mutable.{Map => MMap, Queue => MQueue}
import scala.concurrent.duration.DurationLong

case class SubscribeProgress(snippetId: SnippetId)
private case class Cleanup(snippetId: SnippetId)

class ProgressActor extends Actor {
  type ProgressSource = Source[SnippetProgress, NotUsed]

  private val subscribers = MMap.empty[SnippetId, (ProgressSource, Option[ActorRef])]

  private val queuedMessages = MMap.empty[SnippetId, MQueue[SnippetProgress]]

  override def receive: Receive = {
    case SubscribeProgress(snippetId) =>
      val (source, _) = getOrCreateNewSubscriberInfo(snippetId, self)
      sender ! source

    case snippetProgress: SnippetProgress =>
      snippetProgress.snippetId.foreach { snippetId =>
        getOrCreateNewSubscriberInfo(snippetId, self)
        queuedMessages.getOrElseUpdate(snippetId, MQueue()).enqueue(snippetProgress)
        sendQueuedMessages(snippetId, self)
      }

    case (snippedId: SnippetId, graphStageForwarderActor: ActorRef) =>
      subscribers.get(snippedId).foreach(s => subscribers.update(snippedId, s.copy(_2 = Some(graphStageForwarderActor))))
      sendQueuedMessages(snippedId, self)

    case Cleanup(snippetId) =>
      subscribers.remove(snippetId)
      queuedMessages.remove(snippetId)
  }

  private def getOrCreateNewSubscriberInfo(snippetId: SnippetId, self: ActorRef): (ProgressSource, Option[ActorRef]) = {
    subscribers.getOrElseUpdate(
      snippetId,
      Source.fromGraph(new GraphStageForwarder("outlet-graph-" + snippetId, self, snippetId)) -> None
    )
  }

  private def sendQueuedMessages(snippetId: SnippetId, self: ActorRef): Unit =
    for {
      messageQueue <- queuedMessages.get(snippetId).toSeq
      (_, Some(graphStageForwarderActor)) <- subscribers.get(snippetId).toSeq
      message <- messageQueue.dequeueAll(_ => true)
    } yield {
      graphStageForwarderActor ! message
      if (message.isDone) context.system.scheduler.scheduleOnce(3.seconds, self, Cleanup(snippetId))(context.dispatcher)
    }
}
