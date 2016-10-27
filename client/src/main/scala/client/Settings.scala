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
        case ScalaTargetType.Dotty  => ScalaTarget.Native
        case ScalaTargetType.Native => ScalaTarget.Dotty
      }
    }

    def logo(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM    => "smooth-spiral.png"
        case ScalaTargetType.JS     => "dotty3.svg"
        case ScalaTargetType.Dotty  => "scala-js.svg"
        case ScalaTargetType.Native => "native2.png"
      }
    }

    def labelFor(targetType: ScalaTargetType) = {
      targetType match {
        case ScalaTargetType.JVM    => "Scalac (JVM)"
        case ScalaTargetType.JS     => "Scala.js (Browser)"
        case ScalaTargetType.Dotty  => "Dotty (JVM)"
        case ScalaTargetType.Native => "Native (LLVM)"
      }
    }


    fieldset(`class` := "targets")(
      legend("Target"),
      ul(targetTypes.map(targetType =>
        li(
          img(src := s"/assets/${logo(targetType)}", alt := s"logo for ${labelFor(targetType)}"),
          span(labelFor(targetType))
          // label(
            // input.radio(
            //   checked := scalaTarget.targetType == targetType,
            //   onChange ==> backend.setTarget(defaultTarget(targetType)),
            //   name := "target"
            // ),
            
          // )
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

    target match {
      case ScalaTarget.Jvm(scalaVersion) =>
        fieldset(`class` := "versions")(
          legend("Scala Version"),
          ul(suggestedVersions.map{ case (versionStatus, version) =>
            li(
              label(
                input.radio(
                  checked := scalaVersion == version,
                  onChange ==> backend.setTarget(ScalaTarget.Jvm(version)),
                  name := "version"
                ),
                span(s"${version.binary} ($versionStatus)")
              )
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