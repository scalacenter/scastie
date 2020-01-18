package com.olegych.scastie.util

import akka.actor.ActorRef
import akka.stream.{Outlet, SourceShape}
import akka.stream.stage.{GraphStageLogic, OutHandler}

import scala.collection.mutable.{Queue => MQueue}
import scala.reflect.runtime.universe._

class GraphStageLogicForwarder[T: TypeTag, U: TypeTag](out: Outlet[T], shape: SourceShape[T], coordinator: ActorRef, graphId: U)
    extends GraphStageLogic(shape) {

  setHandler(
    out,
    new OutHandler {
      override def onPull(): Unit = {
        deliver()
      }
    }
  )

  override def preStart(): Unit = {
    val thisGraphStageActorRef = getStageActor(bufferElement).ref
    coordinator ! ((graphId, thisGraphStageActorRef))
  }

  private val buffer = MQueue.empty[T]

  private def deliver(): Unit =
    if (isAvailable(out) && buffer.nonEmpty)
      push[T](out, buffer.dequeue)

  private def bufferElement(receive: (ActorRef, Any)): Unit =
    receive match {
      case (_, element: T @unchecked) =>
        buffer.enqueue(element)
        deliver()
    }

}
