package com.olegych.scastie.client

import scastie.api.SbtRunnerState

object StatusState {
  def empty: StatusState = StatusState(sbtRunners = None)
}

final case class StatusState(sbtRunners: Option[Vector[SbtRunnerState]]) {
  def sbtRunnerCount: Option[Int] = sbtRunners.map(_.size)
  def isSbtOk: Boolean = sbtRunners.exists(_.nonEmpty)
}
