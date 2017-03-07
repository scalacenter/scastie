package com.olegych.scastie
package client

import api._
import App._

import japgolly.scalajs.react._, vdom.all._

object Libraries {

  def renderTarget(scalaTarget: ScalaTarget, backend: App.Backend) = {
    val targetTypes = List(
      ScalaTargetType.JVM,
      ScalaTargetType.Dotty,
      ScalaTargetType.Typelevel,
      ScalaTargetType.JS,
      ScalaTargetType.Native
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

    def logo(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => "smooth-spiral.png"
        case ScalaTargetType.Dotty => "dotty-logo.svg"
        case ScalaTargetType.JS => "scala-js.svg"
        case ScalaTargetType.Native => "native.png"
        case ScalaTargetType.Typelevel => "typelevel.svg"
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
      if (targetType == scalaTarget.targetType) TagMod(`class` := "selected")
      else EmptyTag

    val disabledTargets: Set[ScalaTargetType] = Set(
      ScalaTargetType.Native
    )

    def handler(targetType: ScalaTargetType) =
      if (disabledTargets.contains(targetType)) TagMod(`class` := "disabled")
      else TagMod(onClick ==> backend.setTarget2(defaultTarget(targetType)))

    def vote(targetType: ScalaTargetType) = {
      val voteIssueId: Map[ScalaTargetType, Int] = Map(
        ScalaTargetType.Native -> 50
      )
      voteIssueId.get(targetType) match {
        case Some(id) => {
          val link = s"https://github.com/scalacenter/scastie/issues/$id"
          a(href := link, target := "_blank")("Vote")
        }
        case None => EmptyTag
      }
    }

    fieldset(`class` := "targets")(
      legend("Target"),
      ul(
        targetTypes.map(
          targetType =>
            li(handler(targetType), selected(targetType))(
              img(src := s"/assets/public/${logo(targetType)}",
                  alt := s"logo for ${labelFor(targetType)}"),
              span(labelFor(targetType)),
              vote(targetType)
          )))
    )
  }

  def renderVersions(target: ScalaTarget, backend: App.Backend) = {
    val suggestedVersions = List(
      "2.10.6",
      "2.11.8",
      "2.12.1"
    )

    val allVersions = List(
      "2.12.1",
      "2.12.0",
      "2.12.0-RC2",
      "2.12.0-RC1",
      "2.12.0-M5",
      "2.12.0-M4",
      "2.12.0-M3",
      "2.12.0-M2",
      "2.12.0-M1",
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

    def selected(v1: String, v2: String) =
      if (v1 == v2) TagMod(`class` := "selected")
      else EmptyTag

    def setScalaVersion(targetApply: String => ScalaTarget)(
        e: ReactEventI): Callback =
      backend.setTarget(targetApply(e.target.value))

    val notSupported = div("Not supported")

    def versionSelector(scalaVersion: String,
                        targetApply: String => ScalaTarget) =
      TagMod(
        ul(
          suggestedVersions.map(
            version =>
              li(onClick ==> backend.setTarget2(targetApply(version)),
                 selected(version, scalaVersion))(version))
        ),
        select(name := "scalaVersion",
               value := scalaVersion.toString,
               onChange ==> setScalaVersion(targetApply))(
          allVersions.map(version => option(version.toString))
        )
      )

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

    fieldset(`class` := "versions")(
      legend("Scala Version"),
      versionSelectors
    )
  }

  private val component =
    ReactComponentB[(State, App.Backend)]("Libraries").render_P {
      case (state, backend) =>
        
        val theme = if (state.isDarkTheme) "dark" else "light"

        val worksheetModeSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "toggle selected")
          else EmptyTag

        val worksheetModeToogleLabel =
          if (!state.inputs.worksheetMode) "OFF"
          else "ON"

        val worksheetModeClassSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "toggle selected")
          else EmptyTag

        div(`class` := "libraries")(
          ScaladexSearch(state, backend),
          renderTarget(state.inputs.target, backend),
          renderVersions(state.inputs.target, backend),
          fieldset(
            legend("Options"),
            button(onClick ==> backend.toggleWorksheetMode,
               title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
               worksheetModeSelected,
               `class` := "button", worksheetModeClassSelected)(
              iconic.script,
              p(s"Worksheet $worksheetModeToogleLabel")
            )
          ),
          fieldset(
            legend("Sbt Configuration"),
            div("add more"),
            CodeMirrorEditor(
              CodeMirrorEditor.Settings(value = state.inputs.sbtConfigExtra,
                                        theme = s"solarized $theme",
                                        readOnly = false),
              CodeMirrorEditor.Handler(
                updatedSettings => backend.sbtConfigChange(updatedSettings)
              )
            ),
            hr,
            div("resulting build.sbt"),
            div(`class` := "result-sbt")(
              CodeMirrorEditor(
                CodeMirrorEditor.Settings(value = state.inputs.sbtConfig,
                                          theme = s"solarized $theme",
                                          readOnly = true),
                CodeMirrorEditor.Handler(
                  _ => Callback(())
                )
              )
            ),
            hr,
            div("resulting plugins.sbt"),
            div(`class` := "result-sbt")(
              CodeMirrorEditor(
                CodeMirrorEditor.Settings(value = state.inputs.sbtPluginsConfig,
                                          theme = s"solarized $theme",
                                          readOnly = true),
                CodeMirrorEditor.Handler(
                  _ => Callback(())
                )
              )
            )
          )
        )
    }.build
  def apply(state: State, backend: App.Backend) = component((state, backend))
}
