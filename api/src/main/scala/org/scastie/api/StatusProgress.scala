package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

case class RunnerState[C <: BaseInputs](
  config: C,
  tasks: Vector[TaskId],
  serverState: ServerState,
  hasRunningTask: Boolean
)

object RunnerState {
  implicit def encoder[C <: BaseInputs: Encoder]: Encoder[RunnerState[C]] = deriveEncoder
  implicit def decoder[C <: BaseInputs: Decoder]: Decoder[RunnerState[C]] = deriveDecoder
}

sealed trait StatusProgress

object StatusProgress {
  case object KeepAlive extends StatusProgress
  case class Sbt(runners: Vector[RunnerState[SbtInputs]]) extends StatusProgress
  case class ScalaCli(runners: Vector[RunnerState[ScalaCliInputs]]) extends StatusProgress

  implicit val statusProgressEncoder: Encoder[StatusProgress] = deriveEncoder[StatusProgress]
  implicit val statusProgressDecoder: Decoder[StatusProgress] = deriveDecoder[StatusProgress]
}
