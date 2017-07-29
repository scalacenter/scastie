package com.olegych.scastie.api

import com.olegych.scastie.proto._
import com.olegych.scastie.buildinfo.BuildInfo

object VersionHelper {
  val buildVersion = Version(BuildInfo.version)
}
import VersionHelper._

private[api] trait ScalaTargetExtensionsBase {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
  def sbtConfig: String
  def runtimeDependency: Option[ScalaDependency]
  def show: String
}

private[api] class PlainScalaExtension(base: ScalaTarget, value: ScalaTarget.PlainScala) extends ScalaTargetExtensionsBase{
  def targetType: ScalaTargetType = ???
  def scaladexRequest: Map[String, String] = ???
  def renderSbt(lib: ScalaDependency): String = ???
  def sbtConfig: String = ???
  def runtimeDependency: Option[ScalaDependency] = ???
  def show: String = ???
}
private[api] class TypelevelScalaExtension(base: ScalaTarget, value: ScalaTarget.TypelevelScala) extends ScalaTargetExtensionsBase{
  def targetType: ScalaTargetType = ???
  def scaladexRequest: Map[String, String] = ???
  def renderSbt(lib: ScalaDependency): String = ???
  def sbtConfig: String = ???
  def runtimeDependency: Option[ScalaDependency] = ???
  def show: String = ???
}
private[api] class DottyExtension(base: ScalaTarget, value: ScalaTarget.Dotty) extends ScalaTargetExtensionsBase{
  def targetType: ScalaTargetType = ???
  def scaladexRequest: Map[String, String] = ???
  def renderSbt(lib: ScalaDependency): String = ???
  def sbtConfig: String = ???
  def runtimeDependency: Option[ScalaDependency] = ???
  def show: String = ???
}
private[api] class ScalaJsExtension(base: ScalaTarget, value: ScalaTarget.ScalaJs) extends ScalaTargetExtensionsBase{
  def targetType: ScalaTargetType = ???
  def scaladexRequest: Map[String, String] = ???
  def renderSbt(lib: ScalaDependency): String = ???
  def sbtConfig: String = ???
  def runtimeDependency: Option[ScalaDependency] = ???
  def show: String = ???
}

private[api] class ScalaNativeExtension(
  base: ScalaTarget,
  value: ScalaTarget.ScalaNative)
  extends ScalaTargetExtensionsBase {

  import value._

  def sbtConfig: String = {
    s"""scalaVersion := "$scalaVersion""""
  }

  def runtimeDependency: Option[ScalaDependency] = {
    Some(
      ScalaDependency(
        "org.scastie",
        "runtime-scala",
        base,
        buildVersion
      )
    )
  }

  def targetType = ScalaTargetType.ScalaNative

  def scaladexRequest = Map(
    "target" -> "NATIVE",
    "scalaVersion" -> scalaVersion,
    "scalaNativeVersion" -> scalaNativeVersion
  )

  def renderSbt(lib: ScalaDependency): String = {
    import lib._
    s""""$groupId" %%% "$artifact" % "$version""""
  }

  def show: String = s"Scala-Native $scalaVersion $scalaNativeVersion" 
}


object ScalaNative {
  def unapply(scalaTarget: ScalaTarget): Option[(String, String)]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapScalaNative(ScalaTarget.ScalaNative(scalaVersion, scalaNativeVersion)) =>
        Some((scalaVersion, scalaNativeVersion))
      case _ => None
    }
  }
}

// import com.olegych.scastie.proto.ScalaTargetType

// sealed trait ScalaTarget {
//   def targetType: ScalaTargetType
//   def scaladexRequest: Map[String, String]
//   def renderSbt(lib: ScalaDependency): String
// }

// object ScalaTarget {
//   val allVersions = List(
//     "2.13.0-M1",
//     "2.12.3",
//     "2.12.1",
//     "2.12.0",
//     "2.12.0-RC2",
//     "2.12.0-RC1",
//     "2.12.0-M5",
//     "2.12.0-M4",
//     "2.12.0-M3",
//     "2.12.0-M2",
//     "2.12.0-M1",
//     "2.11.11",
//     "2.11.8",
//     "2.11.6",
//     "2.11.5",
//     "2.11.4",
//     "2.11.3",
//     "2.11.2",
//     "2.11.1",
//     "2.11.0",
//     "2.11.0-RC4",
//     "2.11.0-RC3",
//     "2.11.0-RC1",
//     "2.11.0-M8",
//     "2.11.0-M7",
//     "2.11.0-M6",
//     "2.11.0-M5",
//     "2.11.0-M3",
//     "2.10.6",
//     "2.10.5",
//     "2.10.4-RC3",
//     "2.10.4-RC2",
//     "2.10.3",
//     "2.10.3-RC3",
//     "2.10.3-RC2",
//     "2.10.3-RC1",
//     "2.10.2",
//     "2.10.2-RC2",
//     "2.10.2-RC1",
//     "2.10.1",
//     "2.10.1-RC3",
//     "2.10.1-RC1",
//     "2.10.0",
//     "2.10.0-RC5",
//     "2.10.0-RC4",
//     "2.10.0-RC3",
//     "2.10.0-RC2",
//     "2.10.0-RC1",
//     "2.10.0-M7",
//     "2.10.0-M6",
//     "2.10.0-M5",
//     "2.10.0-M4",
//     "2.10.0-M2",
//     "2.10.0-M1",
//     "2.9.3",
//     "2.9.3-RC2",
//     "2.9.3-RC1",
//     "2.9.2",
//     "2.9.2-RC3",
//     "2.9.2-RC2",
//     "2.9.1-1-RC1",
//     "2.9.1-1",
//     "2.9.0"
//   )

//   private val defaultScalaVersion = "2.12.3"
//   private val defaultScalaJsVersion = "0.6.18"

//   object PlainScala {
//     def default = ScalaTarget.PlainScala(scalaVersion = defaultScalaVersion)
//   }

//   val default = PlainScala.default

//   case class PlainScala(scalaVersion: String) extends ScalaTarget {
//     def targetType = ScalaTargetType.PlainScala
//     def scaladexRequest =
//       Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

//     def renderSbt(lib: ScalaDependency): String = {
//       import lib._
//       s""" "$groupId" %% "$artifact" % "$version" """
//     }
//     override def toString: String = s"Scala $scalaVersion"
//   }

//   object TypelevelScala {
//     def default = ScalaTarget.TypelevelScala(scalaVersion = defaultScalaVersion)
//   }

//   case class TypelevelScala(scalaVersion: String) extends ScalaTarget {
//     def targetType = ScalaTargetType.TypelevelScala
//     def scaladexRequest =
//       Map("target" -> "JVM", "scalaVersion" -> scalaVersion)
//     def renderSbt(lib: ScalaDependency): String = {
//       import lib._
//       s""" "$groupId" %% "$artifact" % "$version" """
//     }

//     override def toString: String = s"Typelevel $scalaVersion"
//   }

//   object ScalaJs {
//     val targetFilename = "fastopt.js"
//     val sourceMapFilename: String = targetFilename + ".map"
//     val sourceFilename = "main.scala"
//     val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"

//     def default =
//       ScalaTarget.ScalaJs(
//         scalaVersion = ScalaTarget.defaultScalaVersion,
//         scalaJsVersion = ScalaTarget.defaultScalaJsVersion
//       )
//   }

//   case class ScalaJs(scalaVersion: String, scalaJsVersion: String)
//       extends ScalaTarget {

//     def targetType = ScalaTargetType.ScalaJs
//     def scaladexRequest = Map(
//       "target" -> "JS",
//       "scalaVersion" -> scalaVersion,
//       "scalaJsVersion" -> scalaJsVersion
//     )
//     def renderSbt(lib: ScalaDependency): String = {
//       import lib._
//       s""" "$groupId" %%% "$artifact" % "$version" """
//     }

//     override def toString: String = s"Scala.Js $scalaVersion $scalaJsVersion"
//   }

//   object ScalaNative {
//     def default =
//       ScalaTarget.ScalaNative(
//         scalaVersion = "2.11.11",
//         scalaNativeVersion = "0.2.1"
//       )
//   }

//   case class ScalaNative(scalaVersion: String, scalaNativeVersion: String)
//       extends ScalaTarget {
//     def targetType = ScalaTargetType.ScalaNative
//     def scaladexRequest = Map(
//       "target" -> "NATIVE",
//       "scalaVersion" -> scalaVersion,
//       "scalaNativeVersion" -> scalaNativeVersion
//     )
//     def renderSbt(lib: ScalaDependency): String = {
//       import lib._
//       s""" "$groupId" %%% "$artifact" % "$version" """
//     }

//     override def toString: String =
//       s"Scala.Js $scalaVersion $scalaNativeVersion"
//   }

//   object Dotty {
//     def default: ScalaTarget =
//       Dotty("0.2.0-RC1")    
//   }

//   case class Dotty(dottyVersion: String) extends ScalaTarget {

//     def targetType = ScalaTargetType.Dotty
//     def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
//     def renderSbt(lib: ScalaDependency): String = {
//       import lib._
//       s""""$groupId" %% "$artifact" % "$version""""
//     }

//     override def toString: String = "Dotty"
//   }
// }
