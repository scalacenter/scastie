package com.olegych.scastie.api

import com.olegych.scastie.proto.{ScalaTarget, ScalaTargetType, ScalaDependency}

object PlainScala {
  def unapply(scalaTarget: ScalaTarget): Option[String]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapPlainScala(ScalaTarget.PlainScala(scalaVersion)) =>
        Some(scalaVersion)
      case _ => None
    }
  }

  def apply(scalaVersion: String): ScalaTarget =
    ScalaTarget(
      value = ScalaTarget.Value.WrapPlainScala(
        ScalaTarget.PlainScala(scalaVersion = scalaVersion)
      )
    )

  val defaultScalaVersion = "2.12.3"
  def default: ScalaTarget = PlainScala(defaultScalaVersion)
}

private[api] class PlainScalaExtension(base: ScalaTarget, value: ScalaTarget.PlainScala) extends ScalaTargetExtensionsBase{
  import value._
  def targetType: ScalaTargetType =
    ScalaTargetType.PlainScala

  def scaladexRequest: Map[String, String] = 
    Map("target" -> "JVM", "scalaVersion" -> scalaVersion)
  
  def renderSbt(lib: ScalaDependency): String =
    renderSbtDouble(lib)

  def sbtConfig: String =
    sbtConfigScalaVersion(scalaVersion)

  def sbtPluginsConfig: String = ""

  def runtimeDependency: Option[ScalaDependency] = 
    VersionHelper.runtimeDependency(base)
  
  def show: String = s"Scala $scalaVersion"
}