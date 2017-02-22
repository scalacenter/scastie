package com.olegych.scastie
package api

import scala.concurrent.Future

import buildinfo.BuildInfo.{version => buildVersion}

import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

trait Api {
  def run(inputs: Inputs): Future[Ressource]
  def save(inputs: Inputs): Future[Ressource]
  def fetch(id: Int): Future[Option[FetchResult]]
  def format(code: FormatRequest): Future[FormatResponse]
  def user(): Future[User]
}

case class FormatRequest(code: String, worksheetMode: Boolean)
case class FormatResponse(formattedCode: Either[String, String])

case class Ressource(id: Int)
case class FetchResult(inputs: Inputs, progresses: List[PasteProgress])

case class Paste(
    id: Int,
    code: String,
    sbt: String
)

sealed trait ScalaTargetType
object ScalaTargetType {

  def parse(targetType: String): Option[ScalaTargetType] = {
    targetType match {
      case "JVM" => Some(JVM)
      case "DOTTY" => Some(Dotty)
      case "JS" => Some(JS)
      case "NATIVE" => Some(Native)
      case "TYPELEVEL" => Some(Typelevel)
      case _ => None
    }
  }

  case object JVM extends ScalaTargetType
  case object Dotty extends ScalaTargetType
  case object JS extends ScalaTargetType
  case object Native extends ScalaTargetType
  case object Typelevel extends ScalaTargetType
}

case class ScalaDependency(groupId: String,
                           artifact: String,
                           target: ScalaTarget,
                           version: String)

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
}
object ScalaTarget {
  private val defaultScalaVersion = "2.11.8"
  private val defaultScalaJsVersion = "0.6.13"

  object Jvm {
    def default = ScalaTarget.Jvm(scalaVersion = defaultScalaVersion)
  }
  case class Jvm(scalaVersion: String) extends ScalaTarget {
    def targetType = ScalaTargetType.JVM
    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion)
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %% "$artifact" % "$version" """
    }
  }

  object Typelevel {
    def default = ScalaTarget.Typelevel(scalaVersion = defaultScalaVersion)
  }
  case class Typelevel(scalaVersion: String) extends ScalaTarget {
    def targetType = ScalaTargetType.Typelevel
    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion)
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %% "$artifact" % "$version" """
    }
  }

  object Js {
    def default =
      ScalaTarget.Js(
        scalaVersion = ScalaTarget.defaultScalaVersion,
        scalaJsVersion = ScalaTarget.defaultScalaJsVersion
      )
  }
  case class Js(scalaVersion: String, scalaJsVersion: String)
      extends ScalaTarget {

    def targetType = ScalaTargetType.JS
    def scaladexRequest = Map(
      "target" -> "JS",
      "scalaVersion" -> scalaVersion,
      "scalaJsVersion" -> scalaJsVersion
    )
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %%% "$artifact" % "$version" """
    }
  }
  case object Native extends ScalaTarget {
    def targetType = ScalaTargetType.Native
    def scaladexRequest = Map(
      "target" -> "NATIVE",
      "scalaVersion" -> "2.11",
      "scalaNativeVersion" -> "0.1.0"
    )
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %%% "$artifact" % "$version" """
    }
  }
  case object Dotty extends ScalaTarget {
    def targetType = ScalaTargetType.Dotty
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""""$groupId" %% "$artifact" % "$version""""
    }
  }
}

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

object Inputs {
  val defaultCode =
    """|List("Hello", "World").mkString("", ", ", "!")
       |
       |help""".stripMargin

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
    sbtPluginsConfigExtra = ""
  )

  implicit val pkl: ReadWriter[Inputs] = upickleMacroRW[Inputs]
}

case class Inputs(
    worksheetMode: Boolean,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFrom: Map[ScalaDependency, Project],
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String
) {
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
    target match {
      case ScalaTarget.Js(_, scalaJsVersion) =>
        s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

      case ScalaTarget.Native =>
        s"""|resolvers += Resolver.sonatypeRepo("snapshots")
            |addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "0.1.0-SNAPSHOT")""".stripMargin

      case ScalaTarget.Dotty =>
        """addSbtPlugin("com.felixmulder" % "sbt-dotty" % "0.1.9")"""

      case _: ScalaTarget.Jvm => ""

      case _: ScalaTarget.Typelevel => ""
    }
  }

  def sbtConfig: String = {
    val (targetConfig, targetDependecy) =
      target match {
        case ScalaTarget.Jvm(scalaVersion) => {
          (
            s"""scalaVersion := "$scalaVersion"""",
            ScalaDependency("org.scastie",
                            "runtime-scala",
                            target,
                            buildVersion)
          )
        }

        case ScalaTarget.Typelevel(scalaVersion) => {
          (
            s"""|scalaVersion := "$scalaVersion"
                |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin,
            ScalaDependency("org.scastie",
                            "runtime-scala",
                            target,
                            buildVersion)
          )
        }

        case ScalaTarget.Js(scalaVersion, _) => {
          (
            s"""|scalaVersion := "$scalaVersion"
                |enablePlugins(ScalaJSPlugin)""".stripMargin,
            ScalaDependency("org.scastie",
                            "runtime-scala",
                            target,
                            buildVersion)
          )
        }
        case ScalaTarget.Dotty => {
          (
            """|// you can set scalaVersion with the lastest http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22ch.epfl.lamp%22%20dotty
               |enablePlugins(DottyPlugin)""".stripMargin,
            ScalaDependency("org.scastie",
                            "runtime-dotty",
                            target,
                            buildVersion)
          )
        }
        case ScalaTarget.Native => {
          (
            s"""|scalaVersion := "2.11.8"
                |resolvers += Resolver.sonatypeRepo("snapshots")""".stripMargin,
            ScalaDependency("org.scastie",
                            "runtime-scala",
                            target,
                            buildVersion)
          )
        }
      }

    val allLibraries = libraries + targetDependecy

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

    s"""|$targetConfig
        |
        |$librariesConfig
        |
        |$sbtConfigExtra""".stripMargin
  }
}

case class PasteProgress(
    id: Int,
    userOutput: Option[String],
    sbtOutput: Option[String],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    done: Boolean,
    timeout: Boolean,
    forcedProgramMode: Boolean
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

case class Problem(severity: Severity, line: Option[Int], message: String)

case class RuntimeError(message: String, line: Option[Int], fullStack: String)

case class Position(start: Int, end: Int)
case class Instrumentation(position: Position, render: Render)

sealed trait Render
case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin = copy(a = a.stripMargin)
  def fold = copy(folded = true)
}

case class User(login: String, name: Option[String], avatar_url: String)
