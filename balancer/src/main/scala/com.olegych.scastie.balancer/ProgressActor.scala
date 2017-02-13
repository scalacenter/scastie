package com.olegych.scastie
package balancer

import api._

import akka.actor.{ActorLogging, Actor}
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}

import  akka.stream.OverflowStrategy.dropHead

import scala.collection.mutable.{Map => MMap}

import scala.concurrent.{Future, Promise}

case class SubscribeProgress(id: String)

class ProgressActor extends Actor with ActorLogging {
  import context._

  type PasteProgressQueue = SourceQueueWithComplete[PasteProgress]
  type PasteProgressSource = Source[PasteProgress, PasteProgressQueue]

  private val subscribers = MMap.empty[String, (PasteProgressSource, Future[PasteProgressQueue])]

  def receive = {
    case SubscribeProgress(id) => {
      println("Subscribe")

      val (source, _) = getOrCreateSource(id)

      sender ! source
    }
    case pasteProgress: PasteProgress => {
      println("Progress")

      val id = pasteProgress.id.toString

      val (_, queue) = getOrCreateSource(id)

      queue.foreach{q =>
        q.offer(pasteProgress)
        println(s"Offer $pasteProgress")

        if(pasteProgress.done) {
          println("Done")
          q.complete()
        }
      }

      ()
    }
  }

  private def getOrCreateSource(id: String): (PasteProgressSource, Future[PasteProgressQueue]) = {
    def createSource(id: String) = {
      val sourceAndQueue = peekMatValue(Source.queue[PasteProgress](
        bufferSize = 100,
        overflowStrategy = dropHead)
      )
      subscribers(id) = sourceAndQueue
      sourceAndQueue
    }

    subscribers.get(id).getOrElse(createSource(id))
  }


  def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }
}
