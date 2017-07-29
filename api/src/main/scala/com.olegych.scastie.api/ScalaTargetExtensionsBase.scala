package com.olegych.scastie.api

import com.olegych.scastie.proto._

private[api] trait ScalaTargetExtensionsBase {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
  def renderSbt(lib: ScalaDependency): String
  def sbtConfig: String
  def sbtPluginsConfig: String
  def runtimeDependency: Option[ScalaDependency]
  def show: String

  protected def sbtConfigScalaVersion(scalaVersion: String): String =
    s"""scalaVersion := "$scalaVersion""""

  protected def renderSbtDouble(lib: ScalaDependency): String = {
    import lib._
    s""" "$groupId" %% "$artifact" % "$version" """
  }

  protected def renderSbtCross(lib: ScalaDependency): String = {
    import lib._
    s""" "$groupId" %%% "$artifact" % "$version" """
  }
}
