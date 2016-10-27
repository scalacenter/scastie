package client

import api._

case class Project(
  organization: String,
  repository: String,
  logo: Option[String] = None,
  artifacts: List[String] = Nil
)

case class ReleaseOptions(groupId: String, artifacts: List[String], versions: List[String])

case class ScalaDependency(groupId: String, artifact: String, target: ScalaTarget, version: String)

// case class MavenReference(groupId: String, artifactId: String, version: String)

case class Version(_1: Int, _2: Int, _3: Int, extra: String = "") {
  def binary: String = s"${_1}.${_2}" // like 2.11
  override def toString: String = s"${_1}.${_2}.${_3}$extra"
}

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
}
object ScalaTarget {
  private val defaultScalaVersion = Version(2, 11, 8)
  private val defaultScalaJsVersion = Version(0, 6, 12)

  case class Jvm(scalaVersion: Version = defaultScalaVersion) extends ScalaTarget {
    def targetType = ScalaTargetType.JVM
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> scalaVersion.binary)
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %% "$artifact" % "$version" """
    }
  }
  case class Js(scalaVersion: Version = defaultScalaVersion, 
                scalaJsVersion: Version = defaultScalaJsVersion) extends ScalaTarget {

    def targetType = ScalaTargetType.JS
    def scaladexRequest = Map(
      "target" -> "JS",
      "scalaVersion" -> scalaVersion.binary,
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
      "target" -> "JS",
      "scalaVersion" -> "2.11",
      "scalaJsVersion" -> "0.6"
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


// input
case class Inputs(
  code: String = "",
  target: ScalaTarget = ScalaTarget.Jvm(),
  libraries: Set[ScalaDependency] = Set()
) {
  def sbtConfig: String = {

    val targetConfig =
      target match {
        case ScalaTarget.Jvm(scalaVersion) => {
          s"scalaVersion := $scalaVersion"
        }
        case ScalaTarget.Js(scalaVersion, _) => {
          // TODO change scalajs version
          s"""|org.scalajs.sbtplugin.ScalaJSPlugin.projectSettings
              |
              |s"scalaVersion := $scalaVersion"
              |""".stripMargin
        }
        case ScalaTarget.Dotty => {
          "com.felixmulder.dotty.plugin.DottyPlugin.projectSettings"
        }
        case ScalaTarget.Native => {
          "scala.scalanative.ScalaNativePlugin.projectSettings"
        }
      }

    val librariesConfig = 
      if(libraries.isEmpty) ""
      else if(libraries.size == 1) s"libraryDependencies += " + target.renderSbt(libraries.head)
      else {
        val nl = "\n"
        val tab = "  "
        "libraryDependencies += " + 
          libraries.map(target.renderSbt).mkString(
            "Seq(" + nl + tab,
            "," + nl + tab,
            nl + ")"
          )
      }
    
    s"""|coursier.CoursierPlugin.projectSettings
        |
        |$targetConfig
        |
        |$librariesConfig""".stripMargin
  }
}

// outputs
case class Outputs(
  console: Vector[String] = Vector(),
  compilationInfos: Set[api.Problem] = Set(),
  instrumentations: Set[api.Instrumentation] = Set()
)

sealed trait Severity
final case object Info extends Severity
final case object Warning extends Severity
final case object Error extends Severity

case class Position(start: Int, end: Int)

case class CompilationInfo(
  severity: Severity,
  position: Position,
  message: String
)