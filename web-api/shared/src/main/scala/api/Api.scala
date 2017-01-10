package api

import scala.concurrent.Future

trait Api {
  def run(inputs: Inputs): Future[Long]
  def fetch(id: Long): Future[Option[Inputs]]
}

case class Paste(
    id: Long,
    code: String,
    sbt: String
)

sealed trait ScalaTargetType
object ScalaTargetType {
  case object JVM    extends ScalaTargetType
  case object Dotty  extends ScalaTargetType
  case object JS     extends ScalaTargetType
  case object Native extends ScalaTargetType
}

case class Version(_1: Int, _2: Int, _3: Int, extra: String = "") {
  def binary: String            = s"${_1}.${_2}" // like 2.11
  override def toString: String = s"${_1}.${_2}.${_3}$extra"
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
  private val defaultScalaVersion   = Version(2, 11, 8)
  private val defaultScalaJsVersion = Version(0, 6, 13)

  object Jvm {
    def default = ScalaTarget.Jvm(scalaVersion = defaultScalaVersion)
  }
  case class Jvm(scalaVersion: Version)
      extends ScalaTarget {
    def targetType = ScalaTargetType.JVM
    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion.binary)
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
  case class Js(scalaVersion: Version,
                scalaJsVersion: Version)
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

object Inputs {
  val defaultCode =
    """|class Playground {
       |  1 + 1
       |}""".stripMargin

  def default = Inputs(
    code = defaultCode,
    target = ScalaTarget.Jvm.default,
    libraries = Set(),
    sbtConfigExtra = "",
    sbtPluginsConfigExtra = ""
  )
}

case class Inputs(
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String
) {

  def sbtPluginsConfig: String = {
    target match {
      case ScalaTarget.Js(_, scalaJsVersion) =>
        s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

      case ScalaTarget.Native =>
        s"""|resolvers += Resolver.sonatypeRepo("snapshots")
            |addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "0.1.0-SNAPSHOT")""".stripMargin

      case ScalaTarget.Dotty => "" //use sbt-dotty

      case _: ScalaTarget.Jvm => ""
    }
  }

  def sbtConfig: String = {

    val targetConfig =
      target match {
        case ScalaTarget.Jvm(scalaVersion) => {
          s"""|libraryDependencies += "org.scastie" %% "runtime-scala" % "0.1.0-SNAPSHOT"
              |scalaVersion := "$scalaVersion"""".stripMargin
        }
        case ScalaTarget.Js(scalaVersion, _) => {
          s"""|libraryDependencies += "org.scastie" %%% "runtime-scala" % "0.1.0-SNAPSHOT"
              |scalaVersion := "$scalaVersion"
              |enablePlugins(ScalaJSPlugin)""".stripMargin
        }
        case ScalaTarget.Dotty => {
          // http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22ch.epfl.lamp%22%20dotty
          s"""|scalaVersion := "0.1-20170105-42eb864-NIGHTLY"
              |scalaOrganization := "ch.epfl.lamp"
              |scalaBinaryVersion := "2.11"
              |autoScalaLibrary := false  
              |scalacOptions += "-color:never"
              |libraryDependencies ++= Seq(
              |  "ch.epfl.lamp" % "scala-library_2.11" % "0.1-20170105-42eb864-NIGHTLY",
              |  "ch.epfl.lamp" % "dotty_2.11"         % "0.1-20170105-42eb864-NIGHTLY" % "scala-tool",
              |  "org.scastie"  % "runtime-dotty_2.11" % "0.1.0-SNAPSHOT"
              |)
              |scalaCompilerBridgeSource := 
              |  ("ch.epfl.lamp" % "dotty-sbt-bridge" % "0.1.1-20170105-42eb864-NIGHTLY" % "component").sources()
              |""".stripMargin
        }
        case ScalaTarget.Native => {
          """|libraryDependencies += "org.scastie" %%% "runtime-scala" % "0.1.0-SNAPSHOT"
             |scalaVersion := "2.11.8"
             |resolvers += Resolver.sonatypeRepo("snapshots")""".stripMargin
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

    s"""|// target
        |$targetConfig
        |// libs
        |$librariesConfig
        |// extra
        |$sbtConfigExtra""".stripMargin
  }
}


case class PasteProgress(
    id: Long,
    output: String,
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    done: Boolean
)

sealed trait Severity
case object Info    extends Severity
case object Warning extends Severity
case object Error   extends Severity

case class Problem(severity: Severity, line: Option[Int], message: String)

case class Position(start: Int, end: Int)
case class Instrumentation(position: Position, render: Render)

sealed trait Render
case class Value(v: String, className: String) extends Render

case class Markdown(a: String, folded: Boolean = false) extends Render {
  def stripMargin = Markdown(a.stripMargin)
  def fold        = copy(folded = true)
}
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin = copy(a = a.stripMargin)
  def fold        = copy(folded = true)
}
