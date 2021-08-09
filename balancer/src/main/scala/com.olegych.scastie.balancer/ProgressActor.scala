package com.olegych.scastie
package balancer

import akka.NotUsed
import akka.{actor => classic}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl.Source
import com.olegych.scastie.api._
import com.olegych.scastie.util.GraphStageForwarder

import scala.collection.mutable.{Map => MMap, Queue => MQueue}
import scala.concurrent.duration.DurationLong
import ProgressActor.{Message, ProgressSource}

case class SubscribeProgress(snippetId: SnippetId, replyTo: ActorRef[ProgressSource]) extends Message

object ProgressActor {
  type ProgressSource = Source[SnippetProgress, NotUsed]

  type Message = ProgressMessage
  private case class Cleanup(snippetId: SnippetId) extends Message

  private case class GraphStageForwarderMsg(
    snippedId: SnippetId,
    graphStageForwarderActor: ActorRef[SnippetProgress]
  ) extends Message

  def apply(): Behavior[Message] =
    Behaviors.supervise {
      Behaviors.setup(new ProgressActor(_))
    }.onFailure(SupervisorStrategy.resume)
}

import ProgressActor._
class ProgressActor private (ctx: ActorContext[Message]) extends AbstractBehavior[Message](ctx) {
  import context.{self, executionContext}

  private val subscribers = MMap.empty[SnippetId, (ProgressSource, Option[ActorRef[SnippetProgress]])]

  private val queuedMessages = MMap.empty[SnippetId, MQueue[SnippetProgress]]

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case SubscribeProgress(snippetId, replyTo) =>
        replyTo ! getOrCreateNewSubscriberInfo(snippetId)

      case snippetProgress: SnippetProgress =>
        snippetProgress.snippetId.foreach { snippetId =>
          getOrCreateNewSubscriberInfo(snippetId)
          queuedMessages.getOrElseUpdate(snippetId, MQueue()).enqueue(snippetProgress)
          sendQueuedMessages(snippetId)
        }

      case GraphStageForwarderMsg(snippedId, graphStageForwarderActor) =>
        subscribers.get(snippedId).foreach { s =>
          subscribers.update(
            snippedId,
            s.copy(_2 = Some(graphStageForwarderActor))
          )
        }
        sendQueuedMessages(snippedId)

      case Cleanup(snippetId) =>
        subscribers.remove(snippetId)
        queuedMessages.remove(snippetId)
    }

    this
  }

  private def getOrCreateNewSubscriberInfo(snippetId: SnippetId): ProgressSource =
    subscribers.getOrElseUpdate(
      snippetId,
      Source.fromGraph(
        new GraphStageForwarder(
          "outlet-graph-" + snippetId,
          //send from com.olegych.scastie.util.GraphStageLogicForwarder.preStart
          context.messageAdapter[(SnippetId, classic.ActorRef)] {
            case (snippedId, graphStageForwarderActor) =>
              GraphStageForwarderMsg(snippedId, graphStageForwarderActor.toTyped[SnippetProgress])
          }.toClassic,
          snippetId
        )
      ) -> None
    )._1

  private def sendQueuedMessages(snippetId: SnippetId): Unit =
    for {
      messageQueue <- queuedMessages.get(snippetId).toSeq
      (_, Some(graphStageForwarderActor)) <- subscribers.get(snippetId).toSeq
      message <- messageQueue.dequeueAll(_ => true)
    } yield {
      graphStageForwarderActor ! message
      if (message.isDone) {
        context.system.scheduler.scheduleOnce(
          3.seconds,
          () => { self ! Cleanup(snippetId) }
        )
      }
    }
}
