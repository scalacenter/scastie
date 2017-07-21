package com.olegych.scastie
package client

import api.Runner

object StatusState {
  def default = StatusState(runners = None)
}

final case class StatusState(runners: Option[Vector[Runner]]) {
  def runnerCount: Option[Int] = runners.map(_.size)
  def isOk: Boolean = runners.exists(_.nonEmpty)
}