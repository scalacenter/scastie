package com.olegych.scastie.client.scli

import com.olegych.scastie.api.Inputs
import com.olegych.scastie.api.ScalaTargetType
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.api.ScalaTarget

object ScalaCliUtils {
  def convertInputsToScalaCli(in: Inputs): Either[Inputs, String] = {
    val Inputs(_isWorksheetMode, code, target, libraries, librariesFromList, sbtConfigExtra, sbtConfigSaved, sbtPluginsConfigExtra, sbtPluginsConfigSaved, isShowingInUserProfile, forked) = in

    if (sbtConfigExtra.size > 0 && sbtConfigExtra != Inputs.default.sbtConfigExtra) {
      Right("Custom SBT config is not supported in Scala-CLI")
    } else if (target.targetType == ScalaTargetType.ScalaCli) {
      Right("Already a Scala-CLI snippet.")
    } else if (target.targetType == ScalaTargetType.Scala2 || target.targetType == ScalaTargetType.Scala3) {
      Left(
        Inputs.default.copy(_isWorksheetMode = _isWorksheetMode,
            code = prependWithDirectives(target.scalaVersion, libraries, code),
            target = ScalaTarget.ScalaCli()
        )
      )
    } else {
      Right(s"Unsupported target ${target.targetType}")
    }
  }

  private def prependWithDirectives(scalaVersion: String, libraries: Set[ScalaDependency], code: String): String = {
    val dependencies = {
      if (libraries.size == 0) "" else {
        "\n" + libraries.map(dep => s"""//> using dep "${dep.groupId}::${dep.artifact}::${dep.version}"""").mkString("\n")
      }
    }

    s"""//> using scala "$scalaVersion"$dependencies
        |//> =============
        |
        |$code""".stripMargin
  }
}