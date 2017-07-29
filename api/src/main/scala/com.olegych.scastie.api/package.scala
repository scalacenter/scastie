package com.olegych.scastie

import com.olegych.scastie.proto._

package object api {
  implicit class InputsExtensions(val inputs: Inputs) extends AnyVal {
    def sbtConfig: String = InputsHelper.sbtConfig(inputs)
    def sbtPluginsConfig: String = InputsHelper.sbtPluginsConfig(inputs)
  }

  implicit class ScalaTargetExtensions(val scalaTarget: ScalaTarget) extends ScalaTargetExtensionsBase {
    private def dispatch: ScalaTargetExtensionsBase = {
      scalaTarget.value match {
        case ScalaTarget.Value.WrapPlainScala(t) =>
          new PlainScalaExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapTypelevelScala(t) =>
          new TypelevelScalaExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapDotty(t) =>
          new DottyExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapScalaJs(t) =>
          new ScalaJsExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapScalaNative(t) =>
          new ScalaNativeExtension(scalaTarget, t)

        case ScalaTarget.Value.Empty =>
          sys.error("ScalaTargetType.Empty")
      }
    }

    def targetType: ScalaTargetType = dispatch.targetType
    def scaladexRequest: Map[String, String] = dispatch.scaladexRequest
    def renderSbt(lib: ScalaDependency): String = dispatch.renderSbt(lib)
    def sbtConfig: String = dispatch.sbtConfig
    def runtimeDependency: Option[ScalaDependency] = dispatch.runtimeDependency
    def show: String = dispatch.show
  }
}
