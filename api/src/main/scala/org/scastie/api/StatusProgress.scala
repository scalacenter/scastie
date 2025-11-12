package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

case class SbtRunnerState(config: SbtInputs, tasks: Vector[TaskId], sbtState: ServerState)

object SbtRunnerState {
  implicit val sbtRunnerStateEncoder: Encoder[SbtRunnerState] = deriveEncoder[SbtRunnerState]
  implicit val sbtRunnerStateDecoder: Decoder[SbtRunnerState] = deriveDecoder[SbtRunnerState]
}

case class ScalaCliRunnerState(config: ScalaCliInputs, tasks: Vector[TaskId], scalaCliState: ServerState)

object ScalaCliRunnerState {
  implicit val scalaCliRunnerStateEncoder: Encoder[ScalaCliRunnerState] = deriveEncoder[ScalaCliRunnerState]
  implicit val scalaCliRunnerStateDecoder: Decoder[ScalaCliRunnerState] = deriveDecoder[ScalaCliRunnerState]
}

sealed trait StatusProgress

object StatusProgress {
  case object KeepAlive extends StatusProgress
  case class Sbt(runners: Vector[SbtRunnerState]) extends StatusProgress
  case class ScalaCli(runners: Vector[ScalaCliRunnerState]) extends StatusProgress

  implicit val statusProgressEncoder: Encoder[StatusProgress] = deriveEncoder[StatusProgress]
  implicit val statusProgressDecoder: Decoder[StatusProgress] = deriveDecoder[StatusProgress]
}
