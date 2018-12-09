package com.olegych.scastie
package balancer

import akka.NotUsed
import akka.actor.{Actor, ActorRef}
import akka.stream.scaladsl.Source
import com.olegych.scastie.api._

import scala.collection.mutable.{Map => MMap, Queue => MQueue}
import com.olegych.scastie.api._
import com.olegych.scastie.util.GraphStageForwarder

case class SubscribeProgress(snippetId: SnippetId)
case class ProgressDone(snippetId: SnippetId)

trait QueuedMessage
case class SnippetProgressMessage(content: SnippetProgress) extends QueuedMessage
case object SignalProgressIsDone extends QueuedMessage

class ProgressActor extends Actor {
  type ProgressSource = Source[SnippetProgress, NotUsed]

  private val subscribers =
    MMap.empty[SnippetId, (ProgressSource, Option[ActorRef])]

  private val queuedMessages =
    MMap.empty[SnippetId, MQueue[QueuedMessage]]

  override def receive: Receive = {

    case SubscribeProgress(snippetId) => {
      val (source, _) = getOrCreateNewSubscriberInfo(snippetId, self)
      sender ! source
    }

    case snippetProgress: SnippetProgress => {
      //scala js content is delivered via script tag at client\src\main\scala\com.olegych.scastie.client\components\Scastie.scala:217
      processSnippedProgress(snippetProgress.copy(scalaJsContent = None, scalaJsSourceMapContent = None), self)
    }

    case (snippedId: SnippetId, graphStageForwarderActor: ActorRef) =>
      updateGraphStageForwarderActor(snippedId, graphStageForwarderActor)
      sendQueuedMessages(snippedId, self)

    case ProgressDone(snippetId) =>
      subscribers.remove(snippetId)
      queuedMessages.remove(snippetId)
  }

  private def getOrCreateNewSubscriberInfo(
      snippetId: SnippetId,
      self: ActorRef
  ): (ProgressSource, Option[ActorRef]) = {

    def createNewSubscriberInfo(
        snippetId: SnippetId,
        self: ActorRef
    ): (ProgressSource, Option[ActorRef]) = {

      val outletName = "outlet-graph-" + snippetId
      val graphStageForwarder = new GraphStageForwarder(
        outletName,
        self,
        snippetId
      )
      val source: ProgressSource = Source.fromGraph(graphStageForwarder)
      (source, None)
    }

    subscribers.getOrElseUpdate(
      snippetId,
      createNewSubscriberInfo(snippetId, self)
    )
  }

  private def updateGraphStageForwarderActor(
      snippetId: SnippetId,
      graphStageForwarderActor: ActorRef
  ): Unit =
    for {
      (source, _) <- subscribers.get(snippetId)
    } yield subscribers.update(snippetId, (source, Some(graphStageForwarderActor)))

  private def processSnippedProgress(snippetProgress: SnippetProgress, self: ActorRef): Unit = {

    snippetProgress.snippetId.foreach { snippetId =>
      getOrCreateNewSubscriberInfo(snippetId, self)
      addSnippetProgressToQueuedMessages(
        snippetId,
        SnippetProgressMessage(snippetProgress)
      )
      if (snippetProgress.isDone)
        addSnippetProgressToQueuedMessages(snippetId, SignalProgressIsDone)
      sendQueuedMessages(snippetId, self)

    }
  }

  private def addSnippetProgressToQueuedMessages(
      snippetId: SnippetId,
      messageToQueue: QueuedMessage
  ): Unit =
    queuedMessages.get(snippetId) match {
      case Some(queue) => queue.enqueue(messageToQueue)
      case None =>
        val queue = MQueue(messageToQueue)
        queuedMessages += (snippetId -> queue)
    }

  private def sendQueuedMessages(snippetId: SnippetId, self: ActorRef): Unit =
    for {
      messageQueue <- queuedMessages.get(snippetId).toSeq
      (_, Some(graphStageForwarderActor)) <- subscribers.get(snippetId).toSeq
      message: QueuedMessage <- messageQueue.dequeueAll(_ => true)
    } yield
      message match {
        case SnippetProgressMessage(content) => {
          graphStageForwarderActor ! content

        }
        case SignalProgressIsDone =>
          self ! ProgressDone(snippetId)
      }
}
