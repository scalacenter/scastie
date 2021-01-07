package com.olegych.scastie.api

import com.olegych.scastie.buildinfo.BuildInfo

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
  def sbtConfig: String
  def sbtPluginsConfig: String
  def sbtRunCommand(worksheetMode: Boolean): String
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
  protected def newBinaryScalaVersion(scalaVersion: String): String = {
    scalaVersion.split('.').head
  }
}

object ScalaTarget {
  import play.api.libs.json._

  implicit object ScalaTargetFormat extends Format[ScalaTarget] {
    private val formatJvm = Json.format[Jvm]
    private val formatJs = Json.format[Js]
    private val formatTypelevel = Json.format[Typelevel]
    private val formatNative = Json.format[Native]
    private val formatScala3 = Json.format[Scala3]

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
        case dotty: Scala3 =>
          formatScala3.writes(dotty) ++ JsObject(Seq("tpe" -> JsString("Scala 3")))
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
                case "Scala3"    => formatScala3.reads(json)
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
      else if (scalaVersion >= "2.13.0") ("org.typelevel", "0.11.1", "full")
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
      ScalaTargetType.Scala2

    def scaladexRequest: Map[String, String] =
      Map("target" -> "JVM", "scalaVersion" -> binaryScalaVersion(scalaVersion))

    def renderSbt(lib: ScalaDependency): String =
      renderSbtDouble(lib)

    def sbtConfig: String = {
      val base = sbtConfigScalaVersion(scalaVersion) + "\n" + hktScalacOptions(scalaVersion)
      if (scalaVersion.startsWith("2.13") || scalaVersion.startsWith("2.12"))
        base + "\n" + "scalacOptions += \"-Ydelambdafy:inline\"" //workaround https://github.com/scala/bug/issues/10782
      else base
    }

    def sbtPluginsConfig: String = partialUnificationSbtPlugin

    def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

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

    def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

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
      "scalaJsVersion" -> (if (scalaJsVersion.startsWith("0.")) binaryScalaVersion(scalaJsVersion)
                           else newBinaryScalaVersion(scalaJsVersion))
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

    def sbtRunCommand(worksheetMode: Boolean): String = "fastOptJS"

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

    def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      runtimeDependencyFrom(this)

    override def toString: String =
      s"Scala-Native $scalaVersion $scalaNativeVersion"
  }

  object Scala3 {
    def default: ScalaTarget = Scala3(BuildInfo.latest3)

    def defaultCode: String =
      """|// You can find more examples here:
         |//   https://github.com/lampepfl/dotty-example-project
         |@main def hello = println("Hi Scala 3!")
         |""".stripMargin
  }

  case class Scala3(dottyVersion: String) extends ScalaTarget {

    def hasWorksheetMode: Boolean = false

    def targetType: ScalaTargetType =
      ScalaTargetType.Scala3

    def scaladexRequest: Map[String, String] =
      Map("target" -> "JVM", "scalaVersion" -> "2.13")

    def renderSbt(lib: ScalaDependency): String =
      s"(${renderSbtDouble(lib)}).withDottyCompat(scalaVersion.value)"

    def sbtConfig: String =
      sbtConfigScalaVersion(dottyVersion)

    def sbtPluginsConfig: String =
      """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.4.6")"""

    def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

    def runtimeDependency: Option[ScalaDependency] =
      None

    override def toString: String =
      s"Scala 3 $dottyVersion"
  }
}
