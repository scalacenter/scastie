package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

case class SbtRunnerState(config: SbtInputs, tasks: Vector[TaskId], sbtState: SbtState)

object SbtRunnerState {
  implicit val sbtRunnerStateEncoder: Encoder[SbtRunnerState] = deriveEncoder[SbtRunnerState]
  implicit val sbtRunnerStateDecoder: Decoder[SbtRunnerState] = deriveDecoder[SbtRunnerState]
}

sealed trait StatusProgress

object StatusProgress {
  case object KeepAlive extends StatusProgress
  case class Sbt(runners: Vector[SbtRunnerState]) extends StatusProgress

  implicit val statusProgressEncoder: Encoder[StatusProgress] = deriveEncoder[StatusProgress]
  implicit val statusProgressDecoder: Decoder[StatusProgress] = deriveDecoder[StatusProgress]
}
