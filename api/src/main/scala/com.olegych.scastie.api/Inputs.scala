package com.olegych.scastie.api

import com.olegych.scastie.proto.Project
import com.olegych.scastie.buildinfo.BuildInfo

import System.{lineSeparator => nl}

object Inputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default = Inputs(
    worksheetMode = true,
    code = defaultCode,
    target = ScalaTarget.default,
    libraries = Set(),
    librariesFrom = Map(),
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
}

case class Inputs(
    worksheetMode: Boolean = false,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFrom: Map[ScalaDependency, Project] = Map(),
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String,
    showInUserProfile: Boolean = false,
    forked: Option[SnippetId] = None
) {

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

  // we only autocomplete for the default configuration
  // https://github.com/scalacenter/scastie/issues/275
  def isEnsimeEnabled: Boolean = isDefault

  def addScalaDependency(scalaDependency: ScalaDependency,
                         project: Project): Inputs = {
    copy(
      libraries = libraries + scalaDependency,
      librariesFrom = librariesFrom + (scalaDependency -> project)
    )
  }

  def removeScalaDependency(scalaDependency: ScalaDependency): Inputs = {
    copy(
      libraries = libraries - scalaDependency,
      librariesFrom = librariesFrom - scalaDependency
    )
  }

  def sbtPluginsConfig: String = {
    val targetConfig =
      target match {
        case ScalaTarget.ScalaJs(_, scalaJsVersion) =>
          s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

        case ScalaTarget.ScalaNative(_, scalaNativeVersion) =>
          s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

        case ScalaTarget.Dotty(_) =>
          """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.3")"""

        case _: ScalaTarget.PlainScala =>
          ""

        case _: ScalaTarget.TypelevelScala =>
          ""
      }

    s"""|$targetConfig
        |addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC7")
        |addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.13")
        |addSbtPlugin("org.scastie" % "sbt-scastie" % "${BuildInfo.version}")
        |$sbtPluginsConfigExtra
        |""".stripMargin

  }

  def sbtConfig: String = {
    val (targetConfig, targetDependency) =
      target match {
        case ScalaTarget.PlainScala(scalaVersion) =>
          (
            s"""scalaVersion := "$scalaVersion"""",
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                BuildInfo.version
              )
            )
          )

        case ScalaTarget.TypelevelScala(scalaVersion) =>
          (
            s"""|scalaVersion := "$scalaVersion"
                |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin,
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                BuildInfo.version
              )
            )
          )

        case ScalaTarget.ScalaJs(scalaVersion, _) =>
          (
            s"""|scalaVersion := "$scalaVersion"
                |enablePlugins(ScalaJSPlugin)
                |artifactPath in (Compile, fastOptJS) := 
                |  baseDirectory.value / "${ScalaTarget.ScalaJs.targetFilename}"
                |scalacOptions += {
                |  val from = (baseDirectory in LocalRootProject).value.toURI.toString
                |  val to = "${ScalaTarget.ScalaJs.sourceUUID}/"
                |  "-P:scalajs:mapSourceURI:" + from + "->" + to
                |}
                """.stripMargin,
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                BuildInfo.version
              )
            )
          )
        case ScalaTarget.Dotty(dottyVersion) =>
          (
            s"""scalaVersion := "$dottyVersion"""",
            None
          )
        case ScalaTarget.ScalaNative(scalaVersion, _) =>
          (
            s"""scalaVersion := "$scalaVersion"""",
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                BuildInfo.version
              )
            )
          )
      }

    val optionalTargetDependency =
      if (worksheetMode) targetDependency
      else None

    val allLibraries =
      optionalTargetDependency.map(libraries + _).getOrElse(libraries)

    val librariesConfig =
      if (allLibraries.isEmpty) ""
      else if (allLibraries.size == 1)
        s"libraryDependencies += " + target.renderSbt(allLibraries.head)
      else {
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

    val ensimeConfig = "ensimeIgnoreMissingDirectories := true"

    s"""|$targetConfig
        |
        |$librariesConfig
        |
        |$sbtConfigExtra
        |
        |$ensimeConfig""".stripMargin
  }
}
