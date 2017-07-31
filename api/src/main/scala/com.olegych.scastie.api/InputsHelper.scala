package com.olegych.scastie.api

import com.olegych.scastie.proto.{
  Inputs, ScalaDependency, ScalaTarget,
  ScalaTargetType, Project, Version,
  LibrariesFrom
}
import com.olegych.scastie.buildinfo.BuildInfo

import System.{lineSeparator => nl}

object InputsHelper {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  val defaultTarget = PlainScala.default

  def default = Inputs(
    worksheetMode = true,
    code = defaultCode,
    target = defaultTarget,
    libraries = Set(),
    librariesFrom = Seq(),
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

  def sbtConfig(inputs: Inputs): String = {
    import inputs._

    val buildVersion = Version(BuildInfo.version)

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
      }

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

  def sbtPluginsConfig(inputs: Inputs): String = {
    import inputs._

    val targetConfig = target.sbtPluginsConfig

    s"""|$targetConfig
        |addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC7")
        |addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.13")
        |addSbtPlugin("org.scastie" % "sbt-scastie" % "${BuildInfo.version}")
        |$sbtPluginsConfigExtra
        |""".stripMargin

  }

  def show(inputs: Inputs): String = {
    import inputs._

    if (inputs == InputsHelper.default) {
      "InputsHelper.default"
    } else if (inputs.copy(code = default.code) == default) {
      "InputsHelper.default" + nl +
        code + nl
    } else {
      import inputs._

      val showSbtConfigExtra =
        if (sbtConfigExtra == default.sbtConfigExtra) "default"
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

  def isDefault(inputs: Inputs): Boolean = {
    inputs.copy(code = "") == default.copy(code = "")
  }

  private def librariesFromMap(inputs: Inputs): Map[ScalaDependency, Project] = {
    inputs.librariesFrom.map(
      libraryFrom => (libraryFrom.scalaDependency, libraryFrom.project)
    ).toMap
  }

  private def libraryFromProto(map: Map[ScalaDependency, Project]): Seq[LibrariesFrom] = {
    map.toSeq.map{
      case (dep, pro) =>
        LibrariesFrom(
          scalaDependency = dep,
          project = pro
        )
    }
  }

  def addScalaDependency(inputs: Inputs,
                         scalaDependency: ScalaDependency,
                         project: Project): Inputs = {

    val librariesFromM = librariesFromMap(inputs)

    inputs.copy(
      libraries = inputs.libraries + scalaDependency,
      librariesFrom = 
        libraryFromProto(
          librariesFromM + (scalaDependency -> project)
        )
    )
  }

  def removeScalaDependency(inputs: Inputs,
                            scalaDependency: ScalaDependency): Inputs = {

    val librariesFromM = librariesFromMap(inputs)

    inputs.copy(
      libraries = inputs.libraries - scalaDependency,
      librariesFrom = 
        libraryFromProto(
          librariesFromM - scalaDependency
        )
    )
  }
}