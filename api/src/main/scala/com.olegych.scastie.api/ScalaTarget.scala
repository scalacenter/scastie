package com.olegych.scastie
package api

import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
}

object ScalaTarget {
  private val defaultScalaVersion = "2.12.2"
  private val defaultScalaJsVersion = "0.6.16"

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
  }

  case object Dotty extends ScalaTarget {
    def default = this

    def targetType = ScalaTargetType.Dotty
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
    def renderSbt(lib: ScalaDependency): String = {
      import lib._
      s""""$groupId" %% "$artifact" % "$version""""
    }
  }

  implicit val pkl: ReadWriter[ScalaTarget] = upickleMacroRW[ScalaTarget]
}
