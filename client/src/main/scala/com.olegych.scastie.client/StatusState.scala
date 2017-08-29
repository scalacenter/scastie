package com.olegych.scastie.client

import com.olegych.scastie.api.{SbtRunnerState, EnsimeRunnerState}

object StatusState {
  def empty: StatusState =
    StatusState(
      sbtRunners = None,
      ensimeRunners = None
    )
}

final case class StatusState(sbtRunners: Option[Vector[SbtRunnerState]],
                             ensimeRunners: Option[Vector[EnsimeRunnerState]]) {
  def sbtRunnerCount: Option[Int] = sbtRunners.map(_.size)
  def isSbtOk: Boolean = sbtRunners.exists(_.nonEmpty)
}
