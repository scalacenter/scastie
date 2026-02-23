package org.scastie.client

import org.scastie.api.{SbtRunnerState, ScalaCliRunnerState}

object StatusState {
  def empty: StatusState = StatusState(sbtRunners = None, scalaCliRunners = None)
}

final case class StatusState(
  sbtRunners: Option[Vector[SbtRunnerState]],
  scalaCliRunners: Option[Vector[ScalaCliRunnerState]]
) {
  def sbtRunnerCount: Option[Int] = sbtRunners.map(_.size)
  def isSbtOk: Boolean            = sbtRunners.exists(_.nonEmpty)
}
