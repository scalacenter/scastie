package com.olegych.scastie.api

import com.olegych.scastie.proto.{ScalaTarget, ScalaTargetType, ScalaDependency}

object TypelevelScala {
  def default: ScalaTarget =
    TypelevelScala(
      scalaVersion = PlainScala.defaultScalaVersion
    )

  def apply(scalaVersion: String): ScalaTarget =
    ScalaTarget(
      value = ScalaTarget.Value.WrapTypelevelScala(
        ScalaTarget.TypelevelScala(
          scalaVersion = scalaVersion
        )
      )
    )

  def unapply(scalaTarget: ScalaTarget): Option[String] = {
    scalaTarget.value match {
      case ScalaTarget.Value
            .WrapTypelevelScala(ScalaTarget.TypelevelScala(scalaVersion)) =>
        Some(scalaVersion)
      case _ => None
    }
  }
}

private[api] class TypelevelScalaExtension(base: ScalaTarget,
                                           value: ScalaTarget.TypelevelScala)
    extends ScalaTargetExtensionsBase {
  import value._

  def targetType: ScalaTargetType =
    ScalaTargetType.TypelevelScala

  def scaladexRequest =
    Map("target" -> "JVM", "scalaVersion" -> scalaVersion)

  def renderSbt(lib: ScalaDependency): String =
    renderSbtDouble(lib)

  def sbtConfig: String = {
    s"""|${sbtConfigScalaVersion(scalaVersion)}
        |scalaOrganization in ThisBuild := "org.typelevel"""".stripMargin
  }

  def sbtPluginsConfig: String = ""

  def runtimeDependency: Option[ScalaDependency] =
    VersionHelper.runtimeDependency(base)

  def show: String = s"Typelevel $scalaVersion"
}
