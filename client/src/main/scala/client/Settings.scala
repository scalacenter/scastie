package client

import api._
import App._

import japgolly.scalajs.react._, vdom.all._

object Settings {

  def renderTarget(scalaTarget: ScalaTarget, backend: App.Backend) = {
    val targetTypes = List(
      ScalaTargetType.JVM,
      ScalaTargetType.Dotty,
      ScalaTargetType.JS,
      ScalaTargetType.Native
    )

    def defaultTarget(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => ScalaTarget.Jvm.default
        case ScalaTargetType.JS => ScalaTarget.Js.default
        case ScalaTargetType.Dotty => ScalaTarget.Dotty
        case ScalaTargetType.Native => ScalaTarget.Native
      }
    }

    def logo(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => "smooth-spiral.png"
        case ScalaTargetType.Dotty => "dotty3.svg"
        case ScalaTargetType.JS => "scala-js.svg"
        case ScalaTargetType.Native => "native.png"
      }
    }

    def labelFor(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM => "Scalac"
        case ScalaTargetType.JS => "Scala.js"
        case ScalaTargetType.Dotty => "Dotty"
        case ScalaTargetType.Native => "Native"
      }
    }

    def selected(targetType: ScalaTargetType) =
      if (targetType == scalaTarget.targetType) TagMod(`class` := "selected")
      else EmptyTag

    val disabledTargets: Set[ScalaTargetType] = Set(
      ScalaTargetType.JS,
      ScalaTargetType.Native
    )

    def handler(targetType: ScalaTargetType) =
      if (disabledTargets.contains(targetType)) TagMod(`class` := "disabled")
      else TagMod(onClick ==> backend.setTarget2(defaultTarget(targetType)))

    fieldset(`class` := "targets")(
      legend("Target"),
      ul(
        targetTypes.map(
          targetType =>
            li(handler(targetType), selected(targetType))(
              img(src := s"/assets/${logo(targetType)}",
                  alt := s"logo for ${labelFor(targetType)}"),
              span(labelFor(targetType))
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

    def setScalaVersion(e: ReactEventI): Callback =
      backend.setTarget(ScalaTarget.Jvm(e.target.value))

    target match {
      case ScalaTarget.Jvm(scalaVersion) =>
        fieldset(`class` := "versions")(
          legend("Scala Version"),
          ul(
            suggestedVersions.map(version =>
              li(onClick ==> backend.setTarget2(ScalaTarget.Jvm(version)),
                 selected(version, scalaVersion))(version))),
          select(name := "scalaVersion",
                 value := scalaVersion.toString,
                 onChange ==> setScalaVersion)(
            allVersions.map(version => option(version.toString))
          )
        )
      case ScalaTarget.Js(scalaVersion, scalaJsVersion) => EmptyTag
      case ScalaTarget.Dotty => EmptyTag
      case ScalaTarget.Native => EmptyTag
    }
  }

  private val component =
    ReactComponentB[(State, App.Backend)]("Settings").render_P {
      case (props, backend) =>
        val theme = if (props.isDarkTheme) "dark" else "light"

        div(`class` := "settings")(
          ScaladexSearch(props, backend),
          renderTarget(props.inputs.target, backend),
          renderVersions(props.inputs.target, backend),
          fieldset(
            legend("SBT"),
            pre(props.inputs.sbtConfig),
            CodeMirrorEditor(
              CodeMirrorEditor.Settings(value = props.inputs.sbtConfigExtra,
                                        theme = s"solarized $theme"),
              CodeMirrorEditor.Handler(updatedSettings =>
                backend.sbtConfigChange(updatedSettings))
            )
          )
        )
    }.build
  def apply(state: State, backend: App.Backend) = component((state, backend))
}
