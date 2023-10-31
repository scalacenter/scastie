package org.scastie.client.scalacli

import org.scastie.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import japgolly.scalajs.react.callback.Callback

object ScalaCliUtils {
  implicit class InputConverter(inputs: BaseInputs) {
    def setTarget(newTarget: ScalaTarget): BaseInputs = {
      inputs -> newTarget match {
        case (sbtInputs: SbtInputs, newSbtScalaTarget: SbtScalaTarget) => sbtInputs.copy(target = newSbtScalaTarget)
        case (scalaCliInputs: ScalaCliInputs, newScalaCliTarget: ScalaCli) => scalaCliInputs.copy(target = newScalaCliTarget)
        case (_: ScalaCliInputs, newSbtScalaTarget: SbtScalaTarget) => convertToSbt(newSbtScalaTarget)
        case (_: SbtInputs, _: ScalaCli) => convertToScalaCli
        case _ => inputs
      }
    }

    private def convertToSbt(newSbtScalaTarget: SbtScalaTarget): SbtInputs = {
      val convertedTarget = newSbtScalaTarget.withScalaVersion(inputs.target.scalaVersion)
      val mappedInputs = inputs.libraries.map(_.copy(target = convertedTarget))

      SbtInputs.default.copy(
        isWorksheetMode = inputs.isWorksheetMode,
        isShowingInUserProfile = false,
        code = inputs.code,
        target = convertedTarget,
        libraries = mappedInputs,
        forked = None
      )
    }

    private def convertToScalaCli: ScalaCliInputs = {
      val scalaCliTarget = ScalaCli(inputs.target.scalaVersion)
      val newLibraries = inputs.libraries.map(_.copy(target = scalaCliTarget))

      val (previousDirectives, remainingCode) = inputs.code.linesIterator.span(_.startsWith("//>"))
      val directives = (scalaCliTarget.versionDirective +: newLibraries.map(_.renderScalaCli).toList).mkString("\n")
      val codeWithoutDirectives = remainingCode.mkString("\n")

      val codeWithDirectives = s"""$directives
                                  |$codeWithoutDirectives""".stripMargin

      ScalaCliInputs.default.copy(
        isWorksheetMode = inputs.isWorksheetMode,
        isShowingInUserProfile = false,
        code = codeWithDirectives,
        target = scalaCliTarget,
        libraries = newLibraries,
        forked = None
      )
    }
  }
}
