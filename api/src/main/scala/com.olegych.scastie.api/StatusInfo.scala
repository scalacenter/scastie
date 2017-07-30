package com.olegych.scastie.api

import com.olegych.scastie.proto.{Runner, StatusProgress}

object StatusInfo {
  def apply(runners: Vector[Runner]): StatusProgress =
    StatusProgress(
      value = StatusProgress.Value.WrapStatusInfo(
        StatusProgress.StatusInfo(
          runners = runners
        )
      )
    )
}
