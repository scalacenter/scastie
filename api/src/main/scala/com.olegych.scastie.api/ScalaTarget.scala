package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

import BuildInfo.defaultScalaVersion
import BuildInfo.defaultScalaJsVersion
import BuildInfo.defaultDottyVersion

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
  def sbtConfig: String
  def sbtPluginsConfig: String
  def sbtRunCommand: String
  def runtimeDependency: Option[ScalaDependency]
  def hasEnsimeSupport: Boolean

  protected def sbtConfigScalaVersion(scalaVersion: String): String =
    s"""scalaVersion := "$scalaVersion""""

  protected def renderSbtDouble(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" %% "$artifact" % "$version""""
  }

  protected def renderSbtCross(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" %%% "$artifact" % "$version""""
  }
}

object ScalaTarget {
  import play.api.libs.json._

  implicit object ScalaTargetFormat extends Format[ScalaTarget] {
    private val formatJvm = Json.format[Jvm]
    private val formatJs = Json.format[Js]
    private val formatTypelevel = Json.format[Typelevel]
    private val formatNative = Json.format[Native]
    private val formatDotty = Json.format[Dotty]

    def writes(target: ScalaTarget): JsValue = {
      target match {
        case jvm: Jvm => {
          formatJvm.writes(jvm).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Jvm")))
        }
        case js: Js => {
          formatJs.writes(js).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Js")))
        }
        case typelevel: Typelevel => {
          formatTypelevel.writes(typelevel).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Typelevel")))
        }
        case native: Native => {
          formatNative.writes(native).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Native")))
        }
        case dotty: Dotty => {
          formatDotty.writes(dotty).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Dotty")))
        }
      }
    }

    def reads(json: JsValue): JsResult[ScalaTarget] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(JsString(tpe)) => {
              tpe match {
                case "Jvm"       => formatJvm.reads(json)
                case "Js"        => formatJs.reads(json)
                case "Typelevel" => formatTypelevel.reads(json)
                case "Native"    => formatNative.reads(json)
                case "Dotty"     => formatDotty.reads(json)
                case _           => JsError(Seq())
              }
            }
            case _ => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }

  private def runtimeDependencyFrom(
      target: ScalaTarget
  ): Option[ScalaDependency] = {
    Some(
      ScalaDependency(
        BuildInfo.organization,
        BuildInfo.runtimeProjectName,
        target,
        BuildInfo.version
      )
    )
  }

  object Jvm {
    def default = ScalaTarget.Jvm(scalaVersion = defaultScalaVersion)
  }

  case class Jvm(scalaVersion: String) extends ScalaTarget {
    def hasEnsimeSupport: Boolean = {
      scalaVersion.startsWith("2.12") ||
      scalaVersion.startsWith("2.11")
    }

    def targetType = ScalaTargetType.JVM
    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(scalaVersion)

    def sbtPluginsConfig: String = ""

    def sbtRunCommand: String = "run"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Scala $scalaVersion"
  }

  object Typelevel {
    def default = ScalaTarget.Typelevel(scalaVersion = "2.12.3-bin-typelevel-4")
  }

  case class Typelevel(scalaVersion: String) extends ScalaTarget {

    def hasEnsimeSupport: Boolean =
      Jvm(scalaVersion).hasEnsimeSupport

    def targetType =
      ScalaTargetType.Typelevel

    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String = {
      s"""|${sbtConfigScalaVersion(scalaVersion)}
          |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin
    }

    def sbtPluginsConfig: String = ""

    def sbtRunCommand: String = "run"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Typelevel $scalaVersion"
  }

  object Js {
    val targetFilename = "fastopt.js"
    val sourceMapFilename: String = targetFilename + ".map"
    val sourceFilename = "main.scala"
    val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"

    def default =
      ScalaTarget.Js(
        scalaVersion = defaultScalaVersion,
        scalaJsVersion = defaultScalaJsVersion
      )
  }

  case class Js(scalaVersion: String, scalaJsVersion: String)
      extends ScalaTarget {

    def hasEnsimeSupport: Boolean =
      Jvm(scalaVersion).hasEnsimeSupport

    def targetType = ScalaTargetType.JS

    def scaladexRequest = Map(
      "target" -> "JS",
      "scalaVersion" -> scalaVersion,
      "scalaJsVersion" -> scalaJsVersion
    )

    def renderSbt(lib: ScalaDependency): String =
      renderSbtCross(lib)

    def sbtConfig: String = {
      s"""|${sbtConfigScalaVersion(scalaVersion)}
          |enablePlugins(ScalaJSPlugin)
          |artifactPath in (Compile, fastOptJS) := baseDirectory.value / "${ScalaTarget.Js.targetFilename}"
          |scalacOptions += {
          |  val from = (baseDirectory in LocalRootProject).value.toURI.toString
          |  val to = "${ScalaTarget.Js.sourceUUID}/"
          |  "-P:scalajs:mapSourceURI:" + from + "->" + to
          |}""".stripMargin
    }

    def sbtPluginsConfig: String =
      s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

    def sbtRunCommand: String = "fastOptJS"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Scala.Js $scalaVersion $scalaJsVersion"
  }

  object Native {
    def default =
      ScalaTarget.Native(
        scalaVersion = "2.11.11",
        scalaNativeVersion = "0.3.1"
      )
  }

  case class Native(scalaVersion: String, scalaNativeVersion: String)
      extends ScalaTarget {

    def hasEnsimeSupport: Boolean =
      Jvm(scalaVersion).hasEnsimeSupport

    def targetType =
      ScalaTargetType.Native

    def scaladexRequest =
      Map(
        "target" -> "NATIVE",
        "scalaVersion" -> scalaVersion,
        "scalaNativeVersion" -> scalaNativeVersion
      )

    def renderSbt(lib: ScalaDependency): String =
      renderSbtCross(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(scalaVersion)

    def sbtPluginsConfig: String =
      s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

    def sbtRunCommand: String = "run"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String =
      s"Scala-Native $scalaVersion $scalaNativeVersion"
  }

  object Dotty {
    def default = Dotty(defaultDottyVersion)

    def defaultCode =
      """|// You can find more examples here:
         |//   https://github.com/lampepfl/dotty-example-project
         |
         |// Name Based Pattern
         |// http://dotty.epfl.ch/docs/reference/changed/pattern-matching.html#name-based-pattern
         |
         |class Nat(val x: Int) {
         |  def get: Int = x
         |  def isEmpty = x < 0
         |}
         |
         |object Nat {
         |  def unapply(x: Int): Nat = new Nat(x)
         |}
         |
         |object Main {
         |  def main(args: Array[String]): Unit = { 
         |    5 match {
         |      case Nat(n) => println(s"$n is a natural number")
         |      case _      => ()
         |    }
         |  }
         |}""".stripMargin
  }

  case class Dotty(dottyVersion: String) extends ScalaTarget {

    def hasEnsimeSupport: Boolean = false

    def targetType =
      ScalaTargetType.Dotty

    def scaladexRequest =
      Map("target" -> "JVM", "scalaVersion" -> "2.11")

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(dottyVersion)

    def sbtPluginsConfig: String =
      """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.4")"""

    def sbtRunCommand: String = "run"

    def runtimeDependency: Option[ScalaDependency] =
      None

    override def toString: String =
      s"Dotty $dottyVersion"
  }
}
