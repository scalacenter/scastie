package com.olegych.scastie.util

import akka.actor.ActorRef
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic}

import scala.reflect.runtime.universe._

class GraphStageForwarder[T: TypeTag, U: TypeTag](
    outletName: String,
    coordinator: ActorRef,
    graphId: U
) extends GraphStage[SourceShape[T]] {
  val out: Outlet[T] = Outlet(outletName)
  override val shape = SourceShape[T](out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogicForwarder(out, shape, coordinator, graphId)
}
