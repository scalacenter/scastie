package com.olegych.scastie
package api

import buildinfo.BuildInfo.{version => buildVersion}
import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

import System.{lineSeparator => nl}

object Inputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default = Inputs(
    worksheetMode = true,
    code = defaultCode,
    target = ScalaTarget.Jvm.default,
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

  implicit val pkl: ReadWriter[Inputs] = upickleMacroRW[Inputs]
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
        case ScalaTarget.Js(_, scalaJsVersion) =>
          s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

        case ScalaTarget.Native(_, scalaNativeVersion) =>
          s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

        case ScalaTarget.Dotty =>
          """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.3")"""

        case _: ScalaTarget.Jvm =>
          ""

        case _: ScalaTarget.Typelevel =>
          ""
      }

    s"""|$targetConfig
        |addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC7")
        |addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.13")
        |addSbtPlugin("org.scastie" % "sbt-scastie" % "$buildVersion")
        |$sbtPluginsConfigExtra
        |""".stripMargin

  }

  def sbtConfig: String = {
    val (targetConfig, targetDependency) =
      target match {
        case ScalaTarget.Jvm(scalaVersion) =>
          (
            s"""scalaVersion := "$scalaVersion"""",
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                buildVersion
              )
            )
          )

        case ScalaTarget.Typelevel(scalaVersion) =>
          (
            s"""|scalaVersion := "$scalaVersion"
                |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin,
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                buildVersion
              )
            )
          )

        case ScalaTarget.Js(scalaVersion, _) =>
          (
            s"""|scalaVersion := "$scalaVersion"
                |enablePlugins(ScalaJSPlugin)
                |artifactPath in (Compile, fastOptJS) := baseDirectory.value / "${ScalaTarget.Js.targetFilename}"
                |scalacOptions += {
                |  val from = (baseDirectory in LocalRootProject).value.toURI.toString
                |  val to = "${ScalaTarget.Js.sourceUUID}/"
                |  "-P:scalajs:mapSourceURI:" + from + "->" + to
                |}
                """.stripMargin,
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                buildVersion
              )
            )
          )
        case ScalaTarget.Dotty =>
          (
            """scalaVersion := "0.2.0-RC1"""",
            None
          )
        case ScalaTarget.Native(scalaVersion, _) =>
          (
            s"""scalaVersion := "$scalaVersion"""",
            Some(
              ScalaDependency(
                "org.scastie",
                "runtime-scala",
                target,
                buildVersion
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
