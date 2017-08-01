package com.olegych.scastie.api

import com.olegych.scastie.proto.{ScalaTarget, ScalaTargetType, ScalaDependency}

object ScalaNative {
  def unapply(scalaTarget: ScalaTarget): Option[(String, String)] = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapScalaNative(
          ScalaTarget.ScalaNative(scalaVersion, scalaNativeVersion)
          ) =>
        Some((scalaVersion, scalaNativeVersion))
      case _ => None
    }
  }

  def apply(scalaVersion: String, scalaNativeVersion: String): ScalaTarget = {
    ScalaTarget(
      value = ScalaTarget.Value.WrapScalaNative(
        ScalaTarget.ScalaNative(
          scalaVersion = scalaVersion,
          scalaNativeVersion = scalaNativeVersion
        )
      )
    )
  }

  def default =
    ScalaNative(
      scalaVersion = "2.11.11",
      scalaNativeVersion = "0.3.1"
    )
}

private[api] class ScalaNativeExtension(base: ScalaTarget,
                                        value: ScalaTarget.ScalaNative)
    extends ScalaTargetExtensionsBase {

  import value._

  def sbtConfig: String =
    sbtConfigScalaVersion(scalaVersion)

  def sbtPluginsConfig: String =
    s"""addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "$scalaNativeVersion")"""

  def runtimeDependency: Option[ScalaDependency] =
    VersionHelper.runtimeDependency(base)

  def targetType = ScalaTargetType.ScalaNative

  def scaladexRequest = Map(
    "target" -> "NATIVE",
    "scalaVersion" -> scalaVersion,
    "scalaNativeVersion" -> scalaNativeVersion
  )

  def renderSbt(lib: ScalaDependency): String =
    renderSbtCross(lib)

  def show: String = s"Scala-Native $scalaVersion $scalaNativeVersion"
}
