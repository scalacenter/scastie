package com.olegych.scastie.api

import play.api.libs.json._
import com.olegych.scastie.buildinfo.BuildInfo

import System.{lineSeparator => nl}

sealed trait BaseInputs {
  def code: String
  def target: ScalaTarget
}

case class ShortInputs(code: String, target: ScalaTarget) extends BaseInputs

object ShortInputs {
  implicit val formatShortInputs: OFormat[ShortInputs] = Json.format[ShortInputs]
}

object Inputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default: Inputs = Inputs(
    _isWorksheetMode = true,
    code = defaultCode,
    target = ScalaTarget.Scala3.default,
    libraries = Set(),
    librariesFromList = List(),
    sbtConfigExtra = """|scalacOptions ++= Seq(
                        |  "-deprecation",
                        |  "-encoding", "UTF-8",
                        |  "-feature",
                        |  "-unchecked"
                        |)""".stripMargin,
    sbtConfigSaved = None,
    sbtPluginsConfigExtra = "",
    sbtPluginsConfigSaved = None,
    isShowingInUserProfile = false,
    forked = None
  )

  implicit val formatInputs: OFormat[Inputs] = {
    val f = Json.format[Inputs]
    OFormat(
      Reads { v =>
        f.reads(
          v.asOpt[JsObject]
            .fold(Json.obj())(
              _ ++ Json.obj(
                "_isWorksheetMode" -> (v \ "isWorksheetMode")
                  .asOpt[Boolean]
                  .orElse((v \ "_isWorksheetMode").asOpt[Boolean])
              )
            )
        )
      },
      f
    )
  }
}

case class Inputs(
    _isWorksheetMode: Boolean,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, Project)],
    sbtConfigExtra: String,
    sbtConfigSaved: Option[String],
    sbtPluginsConfigExtra: String,
    sbtPluginsConfigSaved: Option[String],
    isShowingInUserProfile: Boolean,
    forked: Option[SnippetId] = None
) extends BaseInputs {
  val isWorksheetMode = _isWorksheetMode && target.hasWorksheetMode
  val librariesFrom: Map[ScalaDependency, Project] = librariesFromList.toMap

  private lazy val sbtInputs: (String, String) = (sbtConfig, sbtPluginsConfig)

  def needsReload(other: Inputs): Boolean = {
    sbtInputs != other.sbtInputs
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

      s"""|isWorksheetMode = $isWorksheetMode
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

  lazy val isDefault: Boolean = copy(code = "").withSavedConfig == Inputs.default.copy(code = "").withSavedConfig

  def modifyConfig(inputs: Inputs => Inputs): Inputs = inputs(this).copy(sbtConfigSaved = None, sbtPluginsConfigSaved = None)

  def withSavedConfig: Inputs = copy(sbtConfigSaved = Some(sbtConfigGenerated), sbtPluginsConfigSaved = Some(sbtPluginsConfigGenerated))

  def clearDependencies: Inputs = {
    modifyConfig {
      _.copy(
        libraries = Set(),
        librariesFromList = Nil
      )
    }
  }

  def addScalaDependency(scalaDependency: ScalaDependency, project: Project): Inputs = {
    modifyConfig {
      _.copy(
        libraries = libraries + scalaDependency,
        librariesFromList = (librariesFrom + (scalaDependency -> project)).toList
      )
    }
  }

  def removeScalaDependency(scalaDependency: ScalaDependency): Inputs = {
    modifyConfig {
      _.copy(
        libraries = libraries.filterNot(_.matches(scalaDependency)),
        librariesFromList = librariesFrom.filterNot(_._1.matches(scalaDependency)).toList
      )
    }
  }

  def updateScalaDependency(scalaDependency: ScalaDependency, version: String): Inputs = {
    val newScalaDependency = scalaDependency.copy(version = version)
    val newLibraries = libraries.filterNot(_.matches(scalaDependency)) + newScalaDependency
    val newLibrariesFromList = librariesFromList.collect {
      case (l, p) if l.matches(scalaDependency) =>
        newScalaDependency -> p
      case (l, p) => l -> p
    }
    modifyConfig {
      _.copy(
        libraries = newLibraries,
        librariesFromList = newLibrariesFromList
      )
    }
  }

  lazy val sbtConfig: String =
    mapToConfig(sbtConfigGenerated, sbtConfigExtra)

  lazy val sbtConfigGenerated: String = sbtConfigSaved.getOrElse {
    val targetConfig = target.sbtConfig

    val optionalTargetDependency =
      if (target.hasWorksheetMode) target.runtimeDependency
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

    mapToConfig(targetConfig, librariesConfig)
  }

  lazy val sbtPluginsConfig: String =
    mapToConfig(sbtPluginsConfigGenerated, sbtPluginsConfigExtra)

  lazy val sbtPluginsConfigGenerated: String = sbtPluginsConfigSaved.getOrElse {
    sbtPluginsConfig0(withSbtScastie = true)
  }

  private def mapToConfig(parts: String*): String =
    parts.filter(_.nonEmpty).mkString("\n")

  private def sbtPluginsConfig0(withSbtScastie: Boolean): String = {
    val targetConfig = target.sbtPluginsConfig

    val sbtScastie =
      if (withSbtScastie)
        s"""addSbtPlugin("org.scastie" % "sbt-scastie" % "${BuildInfo.versionRuntime}")"""
      else ""

    mapToConfig(targetConfig, sbtScastie)
  }
}

object EditInputs {
  implicit val formatEditInputs: OFormat[EditInputs] =
    Json.format[EditInputs]
}

case class EditInputs(snippetId: SnippetId, inputs: Inputs)
