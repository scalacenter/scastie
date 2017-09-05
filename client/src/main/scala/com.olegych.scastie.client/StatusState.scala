package com.olegych.scastie.client

import com.olegych.scastie.api.{
  SbtRunnerState,
  EnsimeRunnerState,
  Inputs,
  EnsimeServerState
}

object StatusState {
  def empty: StatusState =
    StatusState(
      sbtRunners = None,
      ensimeRunners = None
    )
}

final case class StatusState(sbtRunners: Option[Vector[SbtRunnerState]],
                             ensimeRunners: Option[Vector[EnsimeRunnerState]]) {
  def ensimeReady(inputs: Inputs): Boolean = {
    ensimeRunners
      .map(
        _.exists(
          runner =>
            !runner.config.needsReload(inputs) &&
              runner.serverState == EnsimeServerState.Ready
        )
      )
      .getOrElse(false)
  }

  def sbtRunnerCount: Option[Int] = sbtRunners.map(_.size)
  def ensimeRunnersCount: Option[Int] = ensimeRunners.map(_.size)
  def isSbtOk: Boolean = sbtRunners.exists(_.nonEmpty)
}
