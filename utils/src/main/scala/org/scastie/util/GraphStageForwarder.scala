package org.scastie.util

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.stream.{Attributes, Outlet, SourceShape}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic}

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
