package com.olegych.scastie.api

import com.olegych.scastie.proto.{ScalaTarget, ScalaTargetType, ScalaDependency}

object Dotty {
  def default: ScalaTarget = Dotty("0.2.0-RC1")

  def apply(dottyVersion: String): ScalaTarget =
    ScalaTarget(
      value = ScalaTarget.Value.WrapDotty(
        ScalaTarget.Dotty(
          dottyVersion = dottyVersion
        )
      )
    )

  def unapply(scalaTarget: ScalaTarget): Option[String]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapDotty(ScalaTarget.Dotty(dottyVersion)) =>
        Some(dottyVersion)
      case _ => None
    }
  }
}


private[api] class DottyExtension(base: ScalaTarget, value: ScalaTarget.Dotty) extends ScalaTargetExtensionsBase{
  import value._
  def targetType: ScalaTargetType =
    ScalaTargetType.Dotty

  def scaladexRequest: Map[String, String] =
    Map("target" -> "JVM", "scalaVersion" -> "2.11")

  def renderSbt(lib: ScalaDependency): String =
    renderSbtDouble(lib)

  def sbtConfig: String =
    sbtConfigScalaVersion(dottyVersion)

  def sbtPluginsConfig: String =
    """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.4")"""

  def runtimeDependency: Option[ScalaDependency] = None

  def show: String = s"Dotty $dottyVersion"
}