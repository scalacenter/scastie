package com.olegych.scastie
package client

import api._
import japgolly.scalajs.react._
import vdom.all._

object BuildSettings {

  def renderTarget(scalaTarget: ScalaTarget, backend: AppBackend) = {

    val targetTypes = List(
      ScalaTargetType.JVM,
      ScalaTargetType.Dotty,
      ScalaTargetType.Typelevel,
      ScalaTargetType.JS//,
      // ScalaTargetType.Native
    )

    def defaultTarget(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => ScalaTarget.Jvm.default
        case ScalaTargetType.JS => ScalaTarget.Js.default
        case ScalaTargetType.Dotty => ScalaTarget.Dotty
        case ScalaTargetType.Native => ScalaTarget.Native
        case ScalaTargetType.Typelevel => ScalaTarget.Typelevel.default
      }
    }

    def labelFor(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => "Scalac"
        case ScalaTargetType.JS => "Scala.js"
        case ScalaTargetType.Dotty => "Dotty"
        case ScalaTargetType.Native => "Native"
        case ScalaTargetType.Typelevel => "Typelevel"
      }
    }

    def selected(targetType: ScalaTargetType) =
      if (targetType == scalaTarget.targetType) TagMod(`checked` := true)
      else TagMod(`checked` := false)

    val disabledTargets: Set[ScalaTargetType] = Set(
      ScalaTargetType.Native
    )

    def handler(targetType: ScalaTargetType) =
      if (disabledTargets.contains(targetType)) TagMod(`class` := "disabled")
      else TagMod(onChange ==> backend.setTarget2(defaultTarget(targetType)))

    div(
      ul(`class` := "target")(
        targetTypes.map { targetType =>
          val targetLabel = labelFor(targetType)
          li(
            input(`type` := "radio",
                  `class` := targetLabel,
                  value := targetLabel,
                  name := "target",
                  handler(targetType),
                  selected(targetType)),
            label(`for` := targetLabel, role := "button", `class` := "radio", targetLabel)
          )
        }.toTagMod
      )
    )
  }

  def renderVersions(target: ScalaTarget, backend: AppBackend) = {
    val suggestedVersions = List(
      "2.13.0-M1",
      "2.11.11",
      "2.12.2"
    )

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

    def setScalaVersion(
        targetApply: String => ScalaTarget
    )(e: ReactEventFromInput): Callback =
      backend.setTarget(targetApply(e.target.value))

    val notSupported = div("Not supported")

    def versionSelector(scalaVersion: String,
                        targetApply: String => ScalaTarget) = {
      def handler(scalaVersion: String) =
        TagMod(onChange ==> backend.setTarget2(targetApply(scalaVersion)))

      def selected(version: String) =
        if (targetApply(version) == target) TagMod(`checked` := true)
        else TagMod(`checked` := false)

      TagMod(
        ul(`class` := "suggestedVersions")(
          suggestedVersions.map { suggestedVersion =>
            li(
              input(`type` := "radio",
                    `class` := suggestedVersion,
                    value := suggestedVersion,
                    name := "scalaV",
                    handler(suggestedVersion),
                    selected(suggestedVersion)),
              label(`for` := suggestedVersion,
                    `class` := "radio",
                    role := "button",
                    suggestedVersion)
            )
          }.toTagMod,
          li(
            input(`type` := "radio",
                  `class` := scalaVersion,
                  value := scalaVersion,
                  name := "scalaV",
                  handler(scalaVersion)),
            label(
              div(`class` := "select-wrapper")(
                select(name := "scalaVersion",
                       value := scalaVersion.toString,
                       onChange ==> setScalaVersion(targetApply))(
                  allVersions.map(version => option(version)).toTagMod
                )
              )
            )
          )
        )
      )
    }

    val versionSelectors =
      target match {
        case ScalaTarget.Jvm(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.Jvm.apply)
        case ScalaTarget.Typelevel(scalaVersion) =>
          versionSelector(scalaVersion, ScalaTarget.Typelevel.apply)
        case ScalaTarget.Dotty => notSupported
        case ScalaTarget.Js(scalaVersion, scalaJsVersion) => notSupported
        case ScalaTarget.Native => notSupported
      }

    div(
      versionSelectors
    )

  }

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("BuildSettings").render_P {
      case (state, backend) =>
        val theme = if (state.isDarkTheme) "dark" else "light"

        val resetButton =
          if (state.inputs.copy(code = "") != Inputs.default.copy(code = "")) {
            div(onClick ==> backend.toggleReset,
                role := "button",
                `class` := "btn",
                title := "Reset your configuration")(
              "Reset"
            )
          } else EmptyVdom

        div(`class` := "build-settings-container")(
          h2(
            span("Target")
          ),
          renderTarget(state.inputs.target, backend),
          h2(
            span("Scala Version")
          ),
          renderVersions(state.inputs.target, backend),
          h2(
            span("Libraries")
          ),
          ScaladexSearch(state, backend),
          h2(
            span("Sbt Configuration")
          ),
          label(`for` := "configuration", "Add more"),
          pre(`class` := "configuration")(
            CodeMirrorEditor(
              CodeMirrorEditor.Settings(value = state.inputs.sbtConfigExtra,
                                        theme = s"solarized $theme",
                                        readOnly = false),
              CodeMirrorEditor.Handler(
                updatedSettings => backend.sbtConfigChange(updatedSettings)
              )
            )
          ),
          div(`class` := "label", "Resulting build.sbt"),
          pre(`class` := "output")(
            CodeMirrorEditor(
              CodeMirrorEditor.Settings(value = state.inputs.sbtConfig,
                                        theme = s"solarized $theme",
                                        readOnly = true),
              CodeMirrorEditor.Handler(
                _ => Callback(())
              )
            ),
            div(`class` := "label", "Resulting plugins.sbt"),
            pre(`class` := "plugins-output")(
              CodeMirrorEditor(
                CodeMirrorEditor.Settings(
                  value = state.inputs.sbtPluginsConfig,
                  theme = s"solarized $theme",
                  readOnly = true
                ),
                CodeMirrorEditor.Handler(
                  _ => Callback(())
                )
              )
            ),
            resetButton
          )
        )
    }.build
  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
