package org.scastie.api

import org.scastie.buildinfo.BuildInfo
import io.circe.generic.semiauto._
import io.circe._
import io.circe.syntax._

sealed trait ScalaTarget {
  val targetType: ScalaTargetType
  val scalaVersion: String

  val binaryScalaVersion: String = {
    val digits = scalaVersion.split('.')
    if (digits.head == "2") digits.init.mkString(".")
    else digits.head
  }

  def runtimeDependency: ScalaDependency =
    ScalaDependency(BuildInfo.organization, BuildInfo.runtimeProjectName, this, BuildInfo.versionRuntime)


}

sealed trait SbtScalaTarget extends ScalaTarget {
  def scaladexRequest: Map[String, String]
  def sbtConfig: String
  def sbtPluginsConfig: String
  def sbtRunCommand(worksheetMode: Boolean): String

  def hasWorksheetMode: Boolean = true

  def renderDependency(lib: ScalaDependency): String

  protected def sbtConfigScalaVersion: String =
    s"""scalaVersion := "$scalaVersion""""

  protected def renderSbtDouble(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" %% "$artifact" % "$version""""
  }

  protected def renderSbtCross(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" %%% "$artifact" % "$version""""
  }

  protected def renderSbtSingle(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" % "$artifact" % "$version""""
  }

  def isJVMTarget: Boolean

  def withScalaVersion(newVersion: String): SbtScalaTarget = {
    this match {
      case Scala2(scalaVersion) => Scala2(newVersion)
      case Typelevel(scalaVersion) => Typelevel(newVersion)
      case Js(scalaVersion, scalaJsVersion) => Js(newVersion, scalaJsVersion)
      case Native(scalaVersion, scalaNativeVersion) => Native(newVersion, scalaNativeVersion)
      case Scala3(scalaVersion) => Scala3(newVersion)
    }
  }
}

object Scala2 {
  def default: Scala2 = Scala2(scalaVersion = BuildInfo.latest213)
  implicit val scala2Encoder: Encoder[Scala2] = deriveEncoder[Scala2]
  implicit val scala2Decoder: Decoder[Scala2] = deriveDecoder[Scala2]
}

case class Scala2(scalaVersion: String) extends SbtScalaTarget {

  val targetType: ScalaTargetType = ScalaTargetType.Scala2

  def scaladexRequest: Map[String, String] =
    Map("target" -> "JVM", "scalaVersion" -> binaryScalaVersion)

  def renderDependency(lib: ScalaDependency): String = renderSbtDouble(lib)

  def sbtConfig: String = {
    val base = sbtConfigScalaVersion + "\n"
    if (scalaVersion.startsWith("2.13") || scalaVersion.startsWith("2.12"))
      base + "\n" + "scalacOptions += \"-Ydelambdafy:inline\"" //workaround https://github.com/scala/bug/issues/10782
    else base
  }

  def sbtPluginsConfig: String = ""

  def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

  def isJVMTarget: Boolean = true

  override def toString: String = s"Scala $scalaVersion"
}

object Typelevel {
  def default: Typelevel = Typelevel(scalaVersion = "2.12.3-bin-typelevel-4")
  implicit val typeLevelEncoder: Encoder[Typelevel] = deriveEncoder[Typelevel]
  implicit val typeLevelDecoder: Decoder[Typelevel] = deriveDecoder[Typelevel]
}

case class Typelevel(scalaVersion: String) extends SbtScalaTarget {

  val targetType: ScalaTargetType = ScalaTargetType.Typelevel

  def scaladexRequest: Map[String, String] =
    Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

  def renderDependency(lib: ScalaDependency): String = renderSbtDouble(lib)

  def sbtConfig: String = {
    s"""|$sbtConfigScalaVersion
        |ThisBuild / scalaOrganization := "org.typelevel"""".stripMargin
  }

  def sbtPluginsConfig: String = ""

  def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

  def isJVMTarget: Boolean = true

  override def toString: String = s"Typelevel $scalaVersion"
}

object Js {
  val targetFilename = "fastopt.js"
  val sourceMapFilename: String = targetFilename + ".map"
  val sourceFilename = "main.scala"
  val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"

  def default: Js = Js(scalaVersion = BuildInfo.jsScalaVersion, scalaJsVersion = BuildInfo.defaultScalaJsVersion)
  implicit val jsEncoder: Encoder[Js] = deriveEncoder[Js]
  implicit val jsDecoder: Decoder[Js] = deriveDecoder[Js]
}

case class Js(scalaVersion: String, scalaJsVersion: String) extends SbtScalaTarget {

  val targetType: ScalaTargetType = ScalaTargetType.JS

  def scaladexRequest: Map[String, String] = Map(
    "target" -> "JS",
    "scalaVersion" -> binaryScalaVersion,
    "scalaJsVersion" -> (if (scalaJsVersion.startsWith("0.")) scalaJsVersion.split('.').init.mkString(".")
                         else scalaJsVersion.split('.').head)
  )

  def renderDependency(lib: ScalaDependency): String = {
    import lib._

    val scalaJsVersionPrefix = s"sjs${BuildInfo.scalaJsVersion.split('.').head}"

    def renderLibWithSuffix(lib: ScalaDependency, suffix: String): String = {
      val artifactWithSuffix = s"${artifact}_${suffix}"
      val newLib = lib.copy(artifact = artifactWithSuffix)
      renderSbtSingle(newLib)
    }

    lib match {
      case ScalaDependency(groupId, artifact, _, _, _) if groupId == "org.scastie" && artifact == "runtime-scala" =>
        scalaVersion match {
          /* 3.0.0 <= _ <= 3.1.2 */
          case v if v.startsWith("3.0") || (v.startsWith("3.1") && v != "3.1.3") =>
            renderLibWithSuffix(lib, s"${scalaJsVersionPrefix}_3.0.2")
          /* >= 3.1.3 */
          case v if v.startsWith("3") =>
            renderLibWithSuffix(lib, s"${scalaJsVersionPrefix}_3.1.3")
          case v if v.startsWith("2.13") =>
            renderLibWithSuffix(lib, s"${scalaJsVersionPrefix}_${BuildInfo.latest213}")
          case v if v.startsWith("2.12") =>
            renderLibWithSuffix(lib, s"${scalaJsVersionPrefix}_${BuildInfo.latest212}")
          case _ => renderSbtCross(lib)
        }
      case _ => renderSbtCross(lib)
    }
  }

  def sbtConfig: String = {
    s"""|$sbtConfigScalaVersion
        |enablePlugins(ScalaJSPlugin)
        |Compile / fastOptJS / artifactPath := baseDirectory.value / "${Js.targetFilename}"
        |scalacOptions += {
        |  val from = (LocalRootProject / baseDirectory).value.toURI.toString
        |  val to = "${Js.sourceUUID}/"
        |  "-${if (scalaVersion.startsWith("3")) "scalajs-mapSourceURI" else "P:scalajs:mapSourceURI"}:" + from + "->" + to
        |}""".stripMargin
  }

  def sbtPluginsConfig: String =
    s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

  def sbtRunCommand(worksheetMode: Boolean): String = "fastOptJS"

  def isJVMTarget: Boolean = false

  override def toString: String = s"Scala.Js $scalaVersion $scalaJsVersion"
}

object Native {
  def default: Native = Native(scalaVersion = "2.11.11", scalaNativeVersion = "0.3.3")
  implicit val nativeEncoder: Encoder[Native] = deriveEncoder[Native]
  implicit val nativeDecoder: Decoder[Native] = deriveDecoder[Native]
}


case class Native(scalaVersion: String, scalaNativeVersion: String) extends SbtScalaTarget {

  val targetType: ScalaTargetType = ScalaTargetType.Native

  def scaladexRequest: Map[String, String] =
    Map(
      "target" -> "NATIVE",
      "scalaVersion" -> binaryScalaVersion,
      "scalaNativeVersion" -> scalaNativeVersion
    )

  def renderDependency(lib: ScalaDependency): String =
    renderSbtCross(lib)

  def sbtConfig: String = sbtConfigScalaVersion

  def sbtPluginsConfig: String =
    s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

  def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

  def isJVMTarget: Boolean = false

  override def toString: String =
    s"Scala-Native $scalaVersion $scalaNativeVersion"
}

object Scala3 {
  def default: Scala3 = Scala3(BuildInfo.stableNext)

  def defaultCode: String =
    """|// You can find more examples here:
       |//   https://github.com/lampepfl/dotty-example-project
       |println("Hi Scala 3!")
       |""".stripMargin

  implicit val scala3Encoder: Encoder[Scala3] = deriveEncoder[Scala3]
  implicit val scala3Decoder: Decoder[Scala3] = deriveDecoder[Scala3]
}

case class Scala3(scalaVersion: String) extends SbtScalaTarget {

  val targetType: ScalaTargetType = ScalaTargetType.Scala3

  def scaladexRequest: Map[String, String] =
    Map("target" -> "JVM", "scalaVersion" -> binaryScalaVersion)

  def renderDependency(lib: ScalaDependency): String = {
    if (lib == runtimeDependency) renderSbtDouble(lib)
    else if (lib.target.binaryScalaVersion.startsWith("2.13"))
      s"${renderSbtDouble(lib)} cross CrossVersion.for3Use2_13"
    else renderSbtDouble(lib)
  }

  def sbtConfig: String = sbtConfigScalaVersion

  def sbtPluginsConfig: String = ""

  def sbtRunCommand(worksheetMode: Boolean): String = if (worksheetMode) "fgRunMain Main" else "fgRun"

  def isJVMTarget: Boolean = true

  override def toString: String =
    s"Scala $scalaVersion"
}



object SbtScalaTarget {
  implicit val sbtScalaTargetEncoder: Encoder[SbtScalaTarget] = deriveEncoder[SbtScalaTarget]
  implicit val sbtScalaTargetDecoder: Decoder[SbtScalaTarget] = deriveDecoder[SbtScalaTarget]
}

object ScalaCli {
  implicit val scalaCliEncoder: Encoder[ScalaCli] = deriveEncoder[ScalaCli]
  implicit val scalaCliDecoder: Decoder[ScalaCli] = deriveDecoder[ScalaCli]

  def default: ScalaCli = ScalaCli(BuildInfo.stableNext)

  def defaultCode: String =
    """|// Hello!
       |// Scastie is compatible with Scala CLI! You can use
       |// directives: https://scala-cli.virtuslab.org/docs/guides/using-directives/
       |
       |println("Hi Scala CLI <3")
    """.stripMargin
}

case class ScalaCli(scalaVersion: String) extends ScalaTarget {
  val targetType: ScalaTargetType = ScalaTargetType.ScalaCli

  def versionDirective = s"//> using scala $scalaVersion"
}

object ScalaTarget {
  implicit val scalaTargetEncoder: Encoder[ScalaTarget] = Encoder.instance {
    case s: Scala2 => Json.obj("Scala2" -> s.asJson)
    case s: Scala3 => Json.obj("Scala3" -> s.asJson)
    case s: ScalaCli => Json.obj("ScalaCli" -> s.asJson)
    case s: Js => Json.obj("Js" -> s.asJson)
    case s: Native => Json.obj("Native" -> s.asJson)
    case s: Typelevel => Json.obj("Typelevel" -> s.asJson)
  }

  implicit val scalaTargetDecoder: Decoder[ScalaTarget] = Decoder.instance { cursor =>
    cursor.keys.flatMap(_.headOption) match {
      case Some("Scala2") => cursor.downField("Scala2").as[Scala2]
      case Some("Scala3") => cursor.downField("Scala3").as[Scala3]
      case Some("ScalaCli") => cursor.downField("ScalaCli").as[ScalaCli]
      case Some("Js") => cursor.downField("Js").as[Js]
      case Some("Native") => cursor.downField("Native").as[Native]
      case Some("Typelevel") => cursor.downField("Typelevel").as[Typelevel]
      case other => Left(DecodingFailure(s"Unknown ScalaTarget type: $other", cursor.history))
    }
  }
}

