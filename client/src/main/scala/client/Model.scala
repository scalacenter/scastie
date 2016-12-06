package client

import api._

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

case class ReleaseOptions(groupId: String,
                          artifacts: List[String],
                          versions: List[String])

case class ScalaDependency(groupId: String,
                           artifact: String,
                           target: ScalaTarget,
                           version: String)

// case class MavenReference(groupId: String, artifactId: String, version: String)

case class Version(_1: Int, _2: Int, _3: Int, extra: String = "") {
  def binary: String            = s"${_1}.${_2}" // like 2.11
  override def toString: String = s"${_1}.${_2}.${_3}$extra"
}

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
}
object ScalaTarget {
  private val defaultScalaVersion   = Version(2, 11, 8)
  private val defaultScalaJsVersion = Version(0, 6, 12)

  case class Jvm(scalaVersion: Version = defaultScalaVersion)
      extends ScalaTarget {
    def targetType = ScalaTargetType.JVM
    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion.binary)
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %% "$artifact" % "$version" """
    }
  }
  case class Js(scalaVersion: Version = defaultScalaVersion,
                scalaJsVersion: Version = defaultScalaJsVersion)
      extends ScalaTarget {

    def targetType = ScalaTargetType.JS
    def scaladexRequest = Map(
      "target"         -> "JS",
      "scalaVersion"   -> scalaVersion.binary,
      "scalaJsVersion" -> scalaJsVersion.binary
    )
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %%% "$artifact" % "$version" """
    }
  }
  case object Native extends ScalaTarget {
    def targetType = ScalaTargetType.Native
    // ... not really
    def scaladexRequest = Map(
      "target"         -> "JS",
      "scalaVersion"   -> "2.11",
      "scalaJsVersion" -> "0.6"
    )
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %%% "$artifact" % "$version" """
    }
  }
  case object Dotty extends ScalaTarget {
    def targetType      = ScalaTargetType.Dotty
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""""$groupId" %% "$artifact" % "$version""""
    }
  }
}

// input
case class Inputs(
    code: String = "",
    target: ScalaTarget = ScalaTarget.Jvm(),
    libraries: Set[ScalaDependency] = Set(),
    sbtConfigExtra: String = ""
) {
  def sbtConfig: String = {

    val targetConfig =
      target match {
        case ScalaTarget.Jvm(scalaVersion) => {
          s"""|coursier.CoursierPlugin.projectSettings
              |libraryDependencies += "org.scastie" %% "runtime-scala" % "0.1.0-SNAPSHOT"
              |scalaVersion := "$scalaVersion"""".stripMargin
        }
        case ScalaTarget.Js(scalaVersion, _) => {
          // TODO change scalajs version
          s"""|coursier.CoursierPlugin.projectSettings
              |org.scalajs.sbtplugin.ScalaJSPlugin.projectSettings
              |scalaVersion := "$scalaVersion"
              |""".stripMargin
        }
        case ScalaTarget.Dotty => {
          // http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22ch.epfl.lamp%22%20dotty
          s"""|scalaVersion := "0.1-20161203-9ceed92-NIGHTLY"
              |scalaOrganization := "ch.epfl.lamp"
              |scalaBinaryVersion := "2.11"
              |autoScalaLibrary := false
              |libraryDependencies ++= Seq(
              |  "ch.epfl.lamp" % "scala-library_2.11" % "0.1-20161203-9ceed92-NIGHTLY",
              |  "ch.epfl.lamp" % "dotty_2.11"         % "0.1-20161203-9ceed92-NIGHTLY" % "scala-tool",
              |  "org.scastie"  % "runtime-dotty_2.11" % "0.1.0-SNAPSHOT"
              |)
              |scalaCompilerBridgeSource := 
              |  ("ch.epfl.lamp" % "dotty-sbt-bridge" % "0.1.1-20161203-9ceed92-NIGHTLY" % "component").sources()
              |""".stripMargin
        }
        case ScalaTarget.Native => {
          """|coursier.CoursierPlugin.projectSettings
             |scalaVersion := "2.11.8"
             |scala.scalanative.sbtplugin.ScalaNativePlugin.projectSettings""".stripMargin
        }
      }

    val librariesConfig =
      if (libraries.isEmpty) ""
      else if (libraries.size == 1)
        s"libraryDependencies += " + target.renderSbt(libraries.head)
      else {
        val nl  = "\n"
        val tab = "  "
        "libraryDependencies ++= " +
          libraries
            .map(target.renderSbt)
            .mkString(
              "Seq(" + nl + tab,
              "," + nl + tab,
              nl + ")"
            )
      }

    s"""|$targetConfig
        |$librariesConfig
        |$sbtConfigExtra""".stripMargin
  }
}

// outputs
case class Outputs(
    console: Vector[String] = Vector(),
    compilationInfos: Set[api.Problem] = Set(),
    instrumentations: Set[api.Instrumentation] = Set()
)

sealed trait Severity
final case object Info    extends Severity
final case object Warning extends Severity
final case object Error   extends Severity

case class Position(start: Int, end: Int)

case class CompilationInfo(
    severity: Severity,
    position: Position,
    message: String
)
