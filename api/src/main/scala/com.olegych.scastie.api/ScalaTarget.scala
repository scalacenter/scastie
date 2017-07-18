package com.olegych.scastie
package api

import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
}

object ScalaTarget {
  val allVersions = List(
    "2.13.0-M1",
    "2.12.2",
    "2.12.1",
    "2.12.0",
    "2.12.0-RC2",
    "2.12.0-RC1",
    "2.12.0-M5",
    "2.12.0-M4",
    "2.12.0-M3",
    "2.12.0-M2",
    "2.12.0-M1",
    "2.11.11",
    "2.11.8",
    "2.11.6",
    "2.11.5",
    "2.11.4",
    "2.11.3",
    "2.11.2",
    "2.11.1",
    "2.11.0",
    "2.11.0-RC4",
    "2.11.0-RC3",
    "2.11.0-RC1",
    "2.11.0-M8",
    "2.11.0-M7",
    "2.11.0-M6",
    "2.11.0-M5",
    "2.11.0-M3",
    "2.10.6",
    "2.10.5",
    "2.10.4-RC3",
    "2.10.4-RC2",
    "2.10.3",
    "2.10.3-RC3",
    "2.10.3-RC2",
    "2.10.3-RC1",
    "2.10.2",
    "2.10.2-RC2",
    "2.10.2-RC1",
    "2.10.1",
    "2.10.1-RC3",
    "2.10.1-RC1",
    "2.10.0",
    "2.10.0-RC5",
    "2.10.0-RC4",
    "2.10.0-RC3",
    "2.10.0-RC2",
    "2.10.0-RC1",
    "2.10.0-M7",
    "2.10.0-M6",
    "2.10.0-M5",
    "2.10.0-M4",
    "2.10.0-M2",
    "2.10.0-M1",
    "2.9.3",
    "2.9.3-RC2",
    "2.9.3-RC1",
    "2.9.2",
    "2.9.2-RC3",
    "2.9.2-RC2",
    "2.9.1-1-RC1",
    "2.9.1-1",
    "2.9.0"
  )

  private val defaultScalaVersion = "2.12.2"
  private val defaultScalaJsVersion = "0.6.18"

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
    override def toString: String = s"Scala $scalaVersion"
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

    override def toString: String = s"Typelevel $scalaVersion"
  }

  object Js {
    val targetFilename = "fastopt.js"
    val sourceMapFilename = targetFilename + ".map"
    val sourceFilename = "main.scala"
    val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"

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

    override def toString: String = s"Scala.Js $scalaVersion $scalaJsVersion"
  }

  object Native {
    def default =
      ScalaTarget.Native(
        scalaVersion = "2.11.11",
        scalaNativeVersion = "0.2.1"
      )
  }

  case class Native(scalaVersion: String, scalaNativeVersion: String)
      extends ScalaTarget {
    def targetType = ScalaTargetType.Native
    def scaladexRequest = Map(
      "target" -> "NATIVE",
      "scalaVersion" -> scalaVersion,
      "scalaNativeVersion" -> scalaNativeVersion
    )
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""" "$groupId" %%% "$artifact" % "$version" """
    }

    override def toString: String =
      s"Scala.Js $scalaVersion $scalaNativeVersion"
  }

  case object Dotty extends ScalaTarget {
    def default = this

    def targetType = ScalaTargetType.Dotty
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""""$groupId" %% "$artifact" % "$version""""
    }

    override def toString: String = "Dotty"
  }

  implicit val pkl: ReadWriter[ScalaTarget] = upickleMacroRW[ScalaTarget]
}
