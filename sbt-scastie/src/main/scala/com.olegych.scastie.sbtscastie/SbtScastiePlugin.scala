package com.olegych.scastie
package sbtscastie

import sbt._
import Keys._

object SbtScastiePlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[sbt.Def.Setting[_]] =
    (CompilerReporter.setting +: RuntimeErrorLogger.settings) ++
        Seq(
          autoStartServer := false
        )
}
