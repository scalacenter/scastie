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
        case ScalaTargetType.JVM    => ScalaTarget.Jvm()
        case ScalaTargetType.JS     => ScalaTarget.Js()
        case ScalaTargetType.Dotty  => ScalaTarget.Dotty
        case ScalaTargetType.Native => ScalaTarget.Native
      }
    }

    def logo(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM    => "smooth-spiral.png"
        case ScalaTargetType.Dotty  => "dotty3.svg"
        case ScalaTargetType.JS     => "scala-js.svg"
        case ScalaTargetType.Native => "native.png"
      }
    }

    def labelFor(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM    => "Scalac"
        case ScalaTargetType.JS     => "Scala.js"
        case ScalaTargetType.Dotty  => "Dotty"
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
      else TagMod(onClick ==> backend.setTarget(defaultTarget(targetType)))

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
      ("old", Version(2, 10, 6)),
      ("stable", Version(2, 11, 8)),
      ("latest", Version(2, 12, 0))
    )

    def selected(v1: Version, v2: Version) =
      if (v1 == v2) TagMod(`class` := "selected")
      else EmptyTag

    target match {
      case ScalaTarget.Jvm(scalaVersion) =>
        fieldset(`class` := "versions")(
          legend("Scala Version"),
          ul(suggestedVersions.map {
            case (versionStatus, version) =>
              li(onClick ==> backend.setTarget(ScalaTarget.Jvm(version)),
                 selected(version, scalaVersion))(
                s"${version.binary} ($versionStatus)"
              )
          })
        )
      case ScalaTarget.Js(scalaVersion, scalaJsVersion) => EmptyTag
      case ScalaTarget.Dotty                            => EmptyTag
      case ScalaTarget.Native                           => EmptyTag
    }
  }

  private val component =
    ReactComponentB[(State, App.Backend)]("Settings").render_P {
      case (props, backend) =>
        val theme = if (props.dark) "dark" else "light"

        div(`class` := "settings")(
          renderTarget(props.inputs.target, backend),
          renderVersions(props.inputs.target, backend),
          ScaladexSearch(props, backend),
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
