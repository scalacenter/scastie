package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

object ScalaVersions {
  def suggestedScalaVersions(tpe: ScalaTargetType): List[String] = {
    val versions = tpe match {
      case ScalaTargetType.Scala3 => List(BuildInfo.stableLTS, BuildInfo.stableNext)
      case ScalaTargetType.JS     => List(BuildInfo.stableLTS, BuildInfo.stableNext, BuildInfo.latest213, BuildInfo.latest212)
      case _                      => List(BuildInfo.latest213, BuildInfo.latest212)
    }
    versions.distinct
  }

  def allVersions(tpe: ScalaTargetType): List[String] = {
    val versions = tpe match {
      case ScalaTargetType.Scala3 =>
        List(
          BuildInfo.latestNext,
          BuildInfo.stableNext,
          BuildInfo.latestLTS,
          BuildInfo.stableLTS,
          "3.6.4",
          "3.6.3",
          "3.6.2",
          // "3.6.1", // hotfix to 3.6.0 - abandoned
          // "3.6.0", Bad release
          "3.5.2",
          "3.5.1",
          "3.5.0",
          "3.4.3",
          "3.4.2",
          "3.4.1",
          "3.4.0",
          "3.3.5",
          "3.3.4",
          "3.3.3",
          // "3.3.2", Bad release
          "3.3.1",
          "3.3.0",
          "3.2.2",
          "3.2.1",
          "3.2.0",
          "3.1.3",
          "3.1.2",
          "3.1.1",
          "3.1.0",
          "3.0.2",
          "3.0.1",
          "3.0.0"
        )
      case ScalaTargetType.JS =>
        allVersions(ScalaTargetType.Scala3) ++ allVersions(ScalaTargetType.Scala2).filter(v => v.startsWith("2.12") || v.startsWith("2.13"))
      case _ =>
        List(
          BuildInfo.latest213,
          "2.13.15",
          "2.13.14",
          "2.13.13",
          "2.13.12",
          "2.13.11",
          "2.13.10",
          "2.13.9",
          "2.13.8",
          "2.13.7",
          "2.13.6",
          "2.13.5",
          "2.13.4",
          "2.13.3",
          "2.13.2",
          "2.13.1",
          "2.13.0",
          BuildInfo.latest212,
          "2.12.19",
          "2.12.18",
          "2.12.17",
          "2.12.16",
          "2.12.15",
          "2.12.14",
          "2.12.13",
          "2.12.12",
          "2.12.11",
          "2.12.10",
          "2.12.9",
          "2.12.8",
          "2.12.7",
          "2.12.6",
          "2.12.5",
          "2.12.4",
          "2.12.3",
          "2.12.2",
          "2.12.1",
          "2.12.0",
          BuildInfo.latest211,
          "2.11.11",
          /* Those two versions were withdrawn.
      "2.11.10",
      "2.11.9",
           */
          "2.11.8",
          "2.11.7",
          "2.11.6",
          "2.11.5",
          "2.11.4",
          "2.11.3",
          "2.11.2",
          "2.11.1",
          "2.11.0",
          BuildInfo.latest210,
          "2.10.6"
        )
    }
    versions.distinct
  }

  def find(tpe: ScalaTargetType, sv: String): String =
    allVersions(tpe).find(_.startsWith(sv)).getOrElse(sv)
}
