package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._
import org.scastie.buildinfo.BuildInfo

import System.{lineSeparator => nl}

sealed trait BaseInputs {
  val isWorksheetMode: Boolean
  val isShowingInUserProfile: Boolean
  val code: String
  val target: ScalaTarget
  val libraries: Set[ScalaDependency]
  val forked: Option[SnippetId]

  def markAsCopied: BaseInputs = {
    this match {
      case s: SbtInputs => s.copy(isShowingInUserProfile = false, forked = None)
      case s: ScalaCliInputs => s.copy(isShowingInUserProfile = false, forked = None)
      case s: ShortInputs => s.copy(isShowingInUserProfile = false, forked = None)
    }
  }

  def toggleWorksheet: BaseInputs = {
    this match {
      case s: SbtInputs      => s.copy(isWorksheetMode = !s.isWorksheetMode)
      case s: ScalaCliInputs => s.copy(isWorksheetMode = !s.isWorksheetMode)
      case s: ShortInputs    => s.copy(isWorksheetMode = !s.isWorksheetMode)
    }
  }

  def copyBaseInput(
    isWorksheetMode: Boolean = this.isWorksheetMode,
    isShowingInUserProfile: Boolean = this.isShowingInUserProfile,
    code: String = this.code,
    libraries: Set[ScalaDependency] = this.libraries,
    forked: Option[SnippetId] = this.forked,
  ): BaseInputs = this match {
    case shortInputs: ShortInputs => shortInputs.copy(
      isWorksheetMode = isWorksheetMode,
      isShowingInUserProfile = isShowingInUserProfile,
      code = code,
      libraries = libraries,
      forked = forked
    )
    case scalaCliInputs: ScalaCliInputs => scalaCliInputs.copy(
      isWorksheetMode = isWorksheetMode,
      isShowingInUserProfile = isShowingInUserProfile,
      code = code,
      forked = forked,
      libraries = libraries
    )
    case sbtInputs: SbtInputs => sbtInputs.copy(
      isWorksheetMode = isWorksheetMode,
      isShowingInUserProfile = isShowingInUserProfile,
      code = code,
      libraries = libraries,
      forked = forked
    )
  }
}

object BaseInputs {
  implicit val baseInputsEncoder: Encoder[BaseInputs] = deriveEncoder[BaseInputs]
  implicit val baseInputDecoder: Decoder[BaseInputs] = deriveDecoder[BaseInputs]
}

case class ShortInputs(code: String, target: ScalaTarget, isWorksheetMode: Boolean, libraries: Set[ScalaDependency], isShowingInUserProfile: Boolean, forked: Option[SnippetId]) extends BaseInputs

object ShortInputs {
  implicit val shortInputsEncoder: Encoder[ShortInputs] = deriveEncoder[ShortInputs]
  implicit val shortInputsDecoder: Decoder[ShortInputs] = deriveDecoder[ShortInputs]
}

object SbtInputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default: SbtInputs = SbtInputs(
    isWorksheetMode = true,
    code = defaultCode,
    target = Scala3.default,
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

  implicit val sbtInputsEncoder: Encoder[SbtInputs] = deriveEncoder[SbtInputs]
  implicit val sbtInputsDecoder: Decoder[SbtInputs] = deriveDecoder[SbtInputs]

}

case class ScalaCliInputs(
  isWorksheetMode: Boolean,
  code: String,
  target: ScalaCli,
  isShowingInUserProfile: Boolean,
  forked: Option[SnippetId] = None,
  libraries: Set[ScalaDependency] = Set.empty
) extends BaseInputs {

}

object ScalaCliInputs {
  val defaultCode = """List("Hello", "World").mkString("", ", ", "!")"""

  def default: ScalaCliInputs = ScalaCliInputs(
    isWorksheetMode = true,
    code = defaultCode,
    target = ScalaCli.default,
    isShowingInUserProfile = false,
    forked = None
  )

  implicit val scalaCliInputsEncoder: Encoder[ScalaCliInputs] = deriveEncoder[ScalaCliInputs]
  implicit val scalaCliInputsDecoder: Decoder[ScalaCliInputs] = deriveDecoder[ScalaCliInputs]
}

case class SbtInputs(
    isWorksheetMode: Boolean,
    code: String,
    target: SbtScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, Project)],
    sbtConfigExtra: String,
    sbtConfigSaved: Option[String],
    sbtPluginsConfigExtra: String,
    sbtPluginsConfigSaved: Option[String],
    isShowingInUserProfile: Boolean,
    forked: Option[SnippetId] = None
) extends BaseInputs {
  val librariesFrom: Map[ScalaDependency, Project] = librariesFromList.toMap

  private lazy val sbtInputs: (String, String) = (sbtConfig, sbtPluginsConfig)

  def needsReload(other: SbtInputs): Boolean = {
    sbtInputs != other.sbtInputs
  }

  override def toString: String = {
    if (this == SbtInputs.default) {
      "Inputs.default"
    } else if (this.copy(code = SbtInputs.default.code) == SbtInputs.default) {
      "Inputs.default" + nl +
        code + nl
    } else {

      val showSbtConfigExtra =
        if (sbtConfigExtra == SbtInputs.default.sbtConfigExtra) "default"
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

  lazy val isDefault: Boolean = copy(code = "").withSavedConfig == SbtInputs.default.copy(code = "").withSavedConfig

  def modifyConfig(inputs: SbtInputs => SbtInputs): SbtInputs = inputs(this).copy(sbtConfigSaved = None, sbtPluginsConfigSaved = None)

  def withSavedConfig: SbtInputs = copy(sbtConfigSaved = Some(sbtConfigGenerated), sbtPluginsConfigSaved = Some(sbtPluginsConfigGenerated))

  def clearDependencies: SbtInputs = {
    modifyConfig {
      _.copy(
        libraries = Set(),
        librariesFromList = Nil
      )
    }
  }

  def addScalaDependency(scalaDependency: ScalaDependency, project: Project): SbtInputs = {
    modifyConfig {
      _.copy(
        libraries = libraries + scalaDependency,
        librariesFromList = (librariesFrom + (scalaDependency -> project)).toList
      )
    }
  }

  def removeScalaDependency(scalaDependency: ScalaDependency): SbtInputs = {
    modifyConfig {
      _.copy(
        libraries = libraries.filterNot(_.matches(scalaDependency)),
        librariesFromList = librariesFrom.filterNot(_._1.matches(scalaDependency)).toList
      )
    }
  }

  def updateScalaDependency(scalaDependency: ScalaDependency, version: String): SbtInputs = {
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
      if (target.hasWorksheetMode) Some(target.runtimeDependency)
      else None

    val allLibraries =
      optionalTargetDependency.map(libraries + _).getOrElse(libraries)

    val librariesConfig =
      if (allLibraries.isEmpty) ""
      else if (allLibraries.size == 1) {
        s"libraryDependencies += " + target.renderDependency(allLibraries.head)
      } else {
        val nl = "\n"
        val tab = "  "
        "libraryDependencies ++= " +
          allLibraries
            .map(target.renderDependency)
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
  implicit val editInputsEncoder: Encoder[EditInputs] = deriveEncoder[EditInputs]
  implicit val editInputsDecoder: Decoder[EditInputs] = deriveDecoder[EditInputs]
}

case class EditInputs(snippetId: SnippetId, inputs: BaseInputs)
