package client

import api._
import App._

import japgolly.scalajs.react._, vdom.all._

object Settings {

  // private[Settings] case class SettingsState(
  //   scalaTargetType: ScalaTargetType,
  //   scalaVersion: Version,
  //   scalaJsVersion: Version
  // ) {
  //   def scalaTarget = {
  //     scalaTargetType match {
  //       case ScalaTargetType.JVM    => ScalaTarget.JVM(scalaVersion)
  //       case ScalaTargetType.JS     => ScalaTarget.JS(scalaVersion, scala)
  //       case ScalaTargetType.Native => ScalaTarget.Native
  //       case ScalaTargetType.Dotty  => ScalaTarget.Dotty
  //     }
  //   }
  // }

  def renderTarget(scalaTarget: ScalaTarget, backend: Backend) = {
    val targetTypes = List(
      ScalaTargetType.JVM,
      ScalaTargetType.JS,
      ScalaTargetType.Dotty,
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
        case ScalaTargetType.Native => "native2.png"
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
      if(targetType == scalaTarget.targetType) TagMod(`class` := "selected")
      else EmptyTag
      
    fieldset(`class` := "targets")(
      legend("Target"),
      ul(targetTypes.map(targetType =>
        li(onClick ==> backend.setTarget(defaultTarget(targetType)), selected(targetType))(
          img(src := s"/assets/${logo(targetType)}", alt := s"logo for ${labelFor(targetType)}"),
          span(labelFor(targetType))
        )
      ))
    )
  }

  def renderVersions(target: ScalaTarget, backend: Backend) = {
    val suggestedVersions = List(
      ("old", Version(2, 10, 6)),
      ("latest", Version(2, 11, 8)),
      ("next", Version(2, 12, 0, "-RC1"))
    )

    def selected(v1: Version, v2: Version) =
      if(v1 == v2) TagMod(`class` := "selected")
      else EmptyTag

    target match {
      case ScalaTarget.Jvm(scalaVersion) =>
        fieldset(`class` := "versions")(
          legend("Scala Version"),
          ul(suggestedVersions.map{ case (versionStatus, version) =>
            li(onClick ==> backend.setTarget(ScalaTarget.Jvm(version)), selected(version, scalaVersion))(
              s"${version.binary} ($versionStatus)"
            )
          })
        )
      case ScalaTarget.Js(scalaVersion, scalaJsVersion) => EmptyTag
      case ScalaTarget.Dotty => EmptyTag
      case ScalaTarget.Native => EmptyTag
    }
  }

  // def renderCodemirrorSettings(settings: Option[codemirror.Options], backend: Backend) = {
  //   fieldset(
  //     legend("Code Editor")
  //   )
  // }

  private val component = ReactComponentB[(State, Backend)]("Settings")
    .render_P { case (state, backend) =>
      div(`class` := "settings")(
        pre(state.inputs.sbtConfig),
        ScaladexSearch(state, backend),
        renderTarget(state.inputs.target, backend),
        renderVersions(state.inputs.target, backend)
        // renderCodemirrorSettings(state.codemirrorSettings, backend)
      )
    }
    .build
  def apply(state: State, backend: Backend) = component((state, backend))
}