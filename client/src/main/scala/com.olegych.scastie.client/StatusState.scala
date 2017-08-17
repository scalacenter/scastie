package com.olegych.scastie.client

import com.olegych.scastie.api.{EnsimeDown, EnsimeStatus, Runner}

object StatusState {
  def default = StatusState(runners = None, ensimeStatus = EnsimeDown)
}

final case class StatusState(runners: Option[Vector[Runner]],
                             ensimeStatus: EnsimeStatus) {
  def runnerCount: Option[Int] = runners.map(_.size)
  def isOk: Boolean = runners.exists(_.nonEmpty)
}
