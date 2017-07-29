package com.olegych.scastie.api

import com.olegych.scastie.proto.{ScalaTarget, ScalaTargetType, ScalaDependency}

object ScalaJs {
  private val defaultScalaJsVersion = "0.6.19"

  def default =
    ScalaTarget.ScalaJs(
      scalaVersion = PlainScala.defaultScalaVersion,
      scalaJsVersion = defaultScalaJsVersion
    )
  
  val targetFilename: String = "fastopt.js"
  val sourceMapFilename: String = targetFilename + ".map"
  val sourceFilename: String = "main.scala"
  val sourceUUID: String = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"
  
  def unapply(scalaTarget: ScalaTarget): Option[(String, String)]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapScalaJs(ScalaTarget.ScalaJs(scalaVersion, scalaJsVersion)) =>
        Some((scalaVersion, scalaJsVersion))
      case _ => None
    }
  }
}

private[api] class ScalaJsExtension(base: ScalaTarget, value: ScalaTarget.ScalaJs) extends ScalaTargetExtensionsBase{
  import value._
  
  def targetType: ScalaTargetType =
    ScalaTargetType.ScalaJs

  def scaladexRequest: Map[String, String] = 
    Map(
      "target" -> "JS",
      "scalaVersion" -> scalaVersion,
      "scalaJsVersion" -> scalaJsVersion
    )

  def renderSbt(lib: ScalaDependency): String =
    renderSbtCross(lib)

  def sbtConfig: String =
    sbtConfigScalaVersion(scalaVersion)

  def sbtPluginsConfig: String =
    s"""addSbtPlugin("org.scala-js" % "sbt-scalajs" % "$scalaJsVersion")"""

  def runtimeDependency: Option[ScalaDependency] =
    VersionHelper.runtimeDependency(base)

  def show: String = s"Scala.Js $scalaVersion $scalaJsVersion"
}