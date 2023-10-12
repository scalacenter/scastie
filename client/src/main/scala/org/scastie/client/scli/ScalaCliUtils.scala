package org.scastie.client.scli

import org.scastie.api._

object ScalaCliUtils {
  def convertInputsToScalaCli(input: SbtInputs): Either[BaseInputs, String] = {

    if (input.sbtConfigExtra.size > 0 && input.sbtConfigExtra != SbtInputs.default.sbtConfigExtra) {
      Right("Custom SBT config is not supported in Scala-CLI")
    } else if (input.target.isInstanceOf[ScalaCli]) {
      Right("Already a Scala-CLI snippet.")
    } else if (input.target.isJVMTarget) {
      Left(
        ScalaCliInputs(
          isWorksheetMode = input.isWorksheetMode,
          code = prependWithDirectives(input.target.scalaVersion, input.libraries, input.code),
          target = ScalaCli(input.target.scalaVersion),
          isShowingInUserProfile = false,
        )
      )
    } else {
      Right(s"Unsupported target ${input.target}")
    }
  }

  private def prependWithDirectives(scalaVersion: String, libraries: Set[ScalaDependency], code: String): String = {
    val dependencies = {
      if (libraries.size == 0) "" else {
        "\n" + libraries.map(dep => s"""//> using dep "${dep.groupId}::${dep.artifact}::${dep.version}"""").mkString("\n")
      }
    }

    s"""//> using scala "$scalaVersion"$dependencies
        |
        |$code""".stripMargin
  }
}
