package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

object ScalaVersions {
  val suggestedScalaVersions = List(BuildInfo.latest213, BuildInfo.latest212)

  val allVersions: List[String] = List(
    BuildInfo.latest213,
    "2.13.1",
    "2.13.0",
    BuildInfo.latest212,
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

  def find(sv: String): String = allVersions.find(_.startsWith(sv)).getOrElse(sv)
}
