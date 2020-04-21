package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
  def sbtConfig: String
  def sbtPluginsConfig: String
  def sbtRunCommand: String
  def runtimeDependency: Option[ScalaDependency]
  def hasWorksheetMode: Boolean

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

  protected def binaryScalaVersion(scalaVersion: String): String = {
    scalaVersion.split('.').init.mkString(".")
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
        case jvm: Jvm =>
          formatJvm.writes(jvm) ++ JsObject(Seq("tpe" -> JsString("Jvm")))
        case js: Js =>
          formatJs.writes(js) ++ JsObject(Seq("tpe" -> JsString("Js")))
        case typelevel: Typelevel =>
          formatTypelevel.writes(typelevel) ++ JsObject(Seq("tpe" -> JsString("Typelevel")))
        case native: Native =>
          formatNative.writes(native) ++ JsObject(Seq("tpe" -> JsString("Native")))
        case dotty: Dotty =>
          formatDotty.writes(dotty) ++ JsObject(Seq("tpe" -> JsString("Dotty")))
      }
    }

    def reads(json: JsValue): JsResult[ScalaTarget] = {
      json match {
        case obj: JsObject =>
          val vs = obj.value
          vs.get("tpe").orElse(vs.get("$type")) match {
            case Some(JsString(tpe)) =>
              tpe match {
                case "Jvm"       => formatJvm.reads(json)
                case "Js"        => formatJs.reads(json)
                case "Typelevel" => formatTypelevel.reads(json)
                case "Native"    => formatNative.reads(json)
                case "Dotty"     => formatDotty.reads(json)
                case _           => JsError(Seq())
              }
            case _ => JsError(Seq())
          }
        case _ => JsError(Seq())
      }
    }
  }

  private def runtimeDependencyFrom(target: ScalaTarget): Option[ScalaDependency] = Some(
    ScalaDependency(
      BuildInfo.organization,
      BuildInfo.runtimeProjectName,
      target,
      BuildInfo.versionRuntime
    )
  )

  object Jvm {
    def default: ScalaTarget = ScalaTarget.Jvm(scalaVersion = BuildInfo.latest213)
  }

  private def partialUnificationSbtPlugin = """addSbtPlugin("org.lyranthe.sbt" % "partial-unification" % "1.1.2")"""
  private def hktScalacOptions(scalaVersion: String) = {
    val (kpOrg, kpVersion, kpCross) =
      if (scalaVersion == "2.13.0-M5") ("org.spire-math", "0.9.9", "binary")
      else if (scalaVersion == "2.13.0-RC1") ("org.spire-math", "0.9.10", "binary")
      else if (scalaVersion == "2.13.0-RC2") ("org.typelevel", "0.10.1", "binary")
      else if (scalaVersion == "2.13.0-RC3") ("org.typelevel", "0.10.2", "binary")
      else if (scalaVersion >= "2.13.0") ("org.typelevel", "0.11.0", "full")
      else ("org.typelevel", "0.10.3", "binary")
    val paradise =
      if (scalaVersion.startsWith("2.10"))
        """libraryDependencies += compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)"""
      else ""
    s"""|scalacOptions += "-language:higherKinds"
        |addCompilerPlugin("${kpOrg}" %% "kind-projector" % "${kpVersion}" cross CrossVersion.${kpCross})
        |$paradise""".stripMargin
  }

  case class Jvm(scalaVersion: String) extends ScalaTarget {
    def hasWorksheetMode: Boolean = {
      scalaVersion.startsWith("2.13") ||
      scalaVersion.startsWith("2.12") ||
      scalaVersion.startsWith("2.11") ||
      scalaVersion.startsWith("2.10")
    }

    def targetType: ScalaTargetType =
      ScalaTargetType.JVM

    def scaladexRequest: Map[String, String] =
      Map("target" -> "JVM", "scalaVersion" -> binaryScalaVersion(scalaVersion))

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(scalaVersion) + "\n" + hktScalacOptions(scalaVersion)

    def sbtPluginsConfig: String = partialUnificationSbtPlugin

    def sbtRunCommand: String = "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Scala $scalaVersion"
  }

  object Typelevel {
    def default: ScalaTarget =
      ScalaTarget.Typelevel(scalaVersion = "2.12.3-bin-typelevel-4")
  }

  case class Typelevel(scalaVersion: String) extends ScalaTarget {

    def hasWorksheetMode: Boolean =
      Jvm(scalaVersion).hasWorksheetMode

    def targetType: ScalaTargetType =
      ScalaTargetType.Typelevel

    def scaladexRequest: Map[String, String] =
      Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String = {
      s"""|${sbtConfigScalaVersion(scalaVersion)}
          |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin
    }

    def sbtPluginsConfig: String = ""

    def sbtRunCommand: String = "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Typelevel $scalaVersion"
  }

  object Js {
    val targetFilename = "fastopt.js"
    val sourceMapFilename: String = targetFilename + ".map"
    val sourceFilename = "main.scala"
    val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"

    def default = ScalaTarget.Js(
      scalaVersion = BuildInfo.jsScalaVersion,
      scalaJsVersion = BuildInfo.defaultScalaJsVersion
    )
  }

  case class Js(scalaVersion: String, scalaJsVersion: String) extends ScalaTarget {

    def hasWorksheetMode: Boolean =
      Jvm(scalaVersion).hasWorksheetMode

    def targetType: ScalaTargetType =
      ScalaTargetType.JS

    def scaladexRequest: Map[String, String] = Map(
      "target" -> "JS",
      "scalaVersion" -> binaryScalaVersion(scalaVersion),
      "scalaJsVersion" -> binaryScalaVersion(scalaJsVersion)
    )

    def renderSbt(lib: ScalaDependency): String =
      renderSbtCross(lib)

    def sbtConfig: String = {
      s"""|${sbtConfigScalaVersion(scalaVersion)}
          |${hktScalacOptions(scalaVersion)}
          |enablePlugins(ScalaJSPlugin)
          |artifactPath in (Compile, fastOptJS) := baseDirectory.value / "${ScalaTarget.Js.targetFilename}"
          |scalacOptions += {
          |  val from = (baseDirectory in LocalRootProject).value.toURI.toString
          |  val to = "${ScalaTarget.Js.sourceUUID}/"
          |  "-P:scalajs:mapSourceURI:" + from + "->" + to
          |}""".stripMargin
    }

    def sbtPluginsConfig: String =
      s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")""" + "\n" + partialUnificationSbtPlugin

    def sbtRunCommand: String = "fastOptJS"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String = s"Scala.Js $scalaVersion $scalaJsVersion"
  }

  object Native {
    def default: Native =
      ScalaTarget.Native(
        scalaVersion = "2.11.11",
        scalaNativeVersion = "0.3.3"
      )
  }

  case class Native(scalaVersion: String, scalaNativeVersion: String) extends ScalaTarget {

    def hasWorksheetMode: Boolean =
      Jvm(scalaVersion).hasWorksheetMode

    def targetType: ScalaTargetType =
      ScalaTargetType.Native

    def scaladexRequest: Map[String, String] =
      Map(
        "target" -> "NATIVE",
        "scalaVersion" -> binaryScalaVersion(scalaVersion),
        "scalaNativeVersion" -> scalaNativeVersion
      )

    def renderSbt(lib: ScalaDependency): String =
      renderSbtCross(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(scalaVersion)

    def sbtPluginsConfig: String =
      s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

    def sbtRunCommand: String = "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String =
      s"Scala-Native $scalaVersion $scalaNativeVersion"
  }

  object Dotty {
    def default: ScalaTarget = Dotty(BuildInfo.latestDotty)

    def defaultCode: String =
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

    def hasWorksheetMode: Boolean = false

    def targetType: ScalaTargetType =
      ScalaTargetType.Dotty

    def scaladexRequest: Map[String, String] =
      Map("target" -> "JVM", "scalaVersion" -> "2.13")

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String =
      sbtConfigScalaVersion(dottyVersion)

    def sbtPluginsConfig: String =
      """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.3.4")"""

    def sbtRunCommand: String = "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      None

    override def toString: String =
      s"Dotty $dottyVersion"
  }
}
