package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

object ScalaVersions {
  def suggestedScalaVersions(tpe: ScalaTargetType): List[String] = tpe match {
    case ScalaTargetType.Scala3 => List(BuildInfo.stable3, BuildInfo.latest3)
    case _ => List(BuildInfo.latest213, BuildInfo.latest212)
  }
  
  def allVersions(tpe: ScalaTargetType): List[String] = tpe match {
    case ScalaTargetType.Scala3 => List(
      BuildInfo.stable3,
      "3.0.1-RC2",
      "3.0.1-RC1",
      "3.0.0-RC3",
      "3.0.0-RC2",
      "3.0.0-RC1",
      "3.0.0-M3",
      "3.0.0-M1"
    )
    case _ => List(
      BuildInfo.latest213,
      "2.13.5",
      "2.13.4",
      "2.13.3",
      "2.13.2",
      "2.13.1",
      "2.13.0",
      BuildInfo.latest212,
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

  def find(tpe: ScalaTargetType, sv: String): String = 
    allVersions(tpe).find(_.startsWith(sv)).getOrElse(sv)
}
