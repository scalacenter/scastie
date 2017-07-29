package com.olegych.scastie.api

import com.olegych.scastie.proto._
import com.olegych.scastie.buildinfo.BuildInfo

object VersionHelper {
  val buildVersion = Version(BuildInfo.version)

  def runtimeDependency(base: ScalaTarget): Option[ScalaDependency] = {
    Some(
      ScalaDependency(
        BuildInfo.organization,
        BuildInfo.runtimeProjectName,
        base,
        buildVersion
      )
    )
  }
}