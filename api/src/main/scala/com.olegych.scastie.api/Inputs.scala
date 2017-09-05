package com.olegych.scastie.api

import play.api.libs.json._
import com.olegych.scastie.buildinfo.BuildInfo

import System.{lineSeparator => nl}

object Inputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default = Inputs(
    worksheetMode = true,
    code = defaultCode,
    target = ScalaTarget.Jvm.default,
    libraries = Set(),
    librariesFromList = List(),
    sbtConfigExtra = """|scalacOptions ++= Seq(
                        |  "-deprecation",
                        |  "-encoding", "UTF-8",
                        |  "-feature",
                        |  "-unchecked"
                        |)""".stripMargin,
    sbtPluginsConfigExtra = "",
    showInUserProfile = false,
    forked = None
  )

  implicit val formatInputs: OFormat[Inputs] =
    Json.format[Inputs]
}

case class Inputs(
    worksheetMode: Boolean = false,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, Project)] = List(),
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String,
    showInUserProfile: Boolean = false,
    forked: Option[SnippetId] = None
) {

  val librariesFrom = librariesFromList.toMap

  def needsReload(other: Inputs): Boolean = {
    sbtConfig != other.sbtConfig ||
    sbtPluginsConfig != other.sbtPluginsConfig
  }

  override def toString: String = {
    if (this == Inputs.default) {
      "Inputs.default"
    } else if (this.copy(code = Inputs.default.code) == Inputs.default) {
      "Inputs.default" + nl +
        code + nl
    } else {

      val showSbtConfigExtra =
        if (sbtConfigExtra == Inputs.default.sbtConfigExtra) "default"
        else sbtConfigExtra

      s"""|worksheetMode = $worksheetMode
          |target = $target
          |libraries
          |${libraries.mkString(nl)}
          |
          |sbtConfigExtra
          |$showSbtConfigExtra
          |
          |sbtPluginsConfigExtra
          |$sbtPluginsConfigExtra
          |
          |code
          |$code
          |""".stripMargin
    }
  }

  def isDefault: Boolean = copy(code = "") == Inputs.default.copy(code = "")

  def addScalaDependency(scalaDependency: ScalaDependency,
                         project: Project): Inputs = {
    copy(
      libraries = libraries + scalaDependency,
      librariesFromList = (librariesFrom + (scalaDependency -> project)).toList
    )
  }

  def removeScalaDependency(scalaDependency: ScalaDependency): Inputs = {
    copy(
      libraries = libraries - scalaDependency,
      librariesFromList = (librariesFrom - scalaDependency).toList
    )
  }

  def hasEnsimeSupport: Boolean = {
    isDefault
  }

  def sbtConfig: String = {
    val targetConfig = target.sbtConfig

    val optionalTargetDependency =
      if (worksheetMode) target.runtimeDependency
      else None

    val allLibraries =
      optionalTargetDependency.map(libraries + _).getOrElse(libraries)

    val librariesConfig =
      if (allLibraries.isEmpty) ""
      else if (allLibraries.size == 1) {
        s"libraryDependencies += " + target.renderSbt(allLibraries.head)
      } else {
        val nl = "\n"
        val tab = "  "
        "libraryDependencies ++= " +
          allLibraries
            .map(target.renderSbt)
            .mkString(
              "Seq(" + nl + tab,
              "," + nl + tab,
              nl + ")"
            )
      }

    val ensimeConfig =
      if (hasEnsimeSupport) "ensimeIgnoreMissingDirectories := true"
      else ""

    s"""|$targetConfig
        |
        |$librariesConfig
        |
        |$sbtConfigExtra
        |
        |$ensimeConfig""".stripMargin
  }

  def sbtPluginsConfig: String = {
    sbtPluginsConfig0(withSbtScasite = true)
  }

  def sbtPluginsConfigWithoutSbtScastie: String = {
    sbtPluginsConfig0(withSbtScasite = false)
  }

  private def sbtPluginsConfig0(withSbtScasite: Boolean): String = {
    val targetConfig = target.sbtPluginsConfig

    val ensimeConfig =
      if (hasEnsimeSupport)
        s"""addSbtPlugin("org.ensime" % "sbt-ensime" % "${BuildInfo.latestSbtEnsime}")"""
      else ""

    val sbtScastie =
      if (withSbtScasite)
        s"""addSbtPlugin("org.scastie" % "sbt-scastie" % "${BuildInfo.version}")"""
      else ""

    s"""|$targetConfig
        |addSbtPlugin("io.get-coursier" % "sbt-coursier" % "${BuildInfo.latestCoursier}")
        |$sbtScastie
        |$sbtPluginsConfigExtra
        |$ensimeConfig
        |""".stripMargin

  }
}

object EditInputs {
  implicit val formatEditInputs: OFormat[EditInputs] =
    Json.format[EditInputs]
}

case class EditInputs(snippetId: SnippetId, inputs: Inputs)
