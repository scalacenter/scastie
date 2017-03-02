package com.olegych.scastie
package sbtscastie

import sbt._

object ScastiePlugin extends AutoPlugin {

  override def requires = sbt.plugins.JvmPlugin
  override def trigger = allRequirements

  override lazy val projectSettings: Seq[sbt.Def.Setting[_]] =
    CompilerReporter.setting +:
      RuntimeErrorLogger.settings
}
