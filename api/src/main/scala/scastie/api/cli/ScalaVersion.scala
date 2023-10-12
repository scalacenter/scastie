package scastie.api.cli

import scastie.api.ScalaTarget
import scastie.api.ScalaCli

object ScalaVersion {
  def targetFromVersion(version: String): ScalaCli = {
    val nightlyVersions = List("2.nightly", "2.12.nightly", "2.13.nightly", "3.nightly")

    if (nightlyVersions.contains(version)) {
      ScalaCli(latestNightly(version.stripSuffix(".nightly")))
    } else {
      ScalaCli(version)
    }
  }

  private def latestNightly(scalaBinaryVersion: String): String = {
    ""

  }

}
