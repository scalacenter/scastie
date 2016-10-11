package client

import App._

import japgolly.scalajs.react._, vdom.all._

object Settings {

  def renderTarget(scalaTarget: ScalaTarget, backend: Backend) = {
    val targets = List(ScalaTargetType.JVM, ScalaTargetType.Dotty, ScalaTargetType.JS) //, ScalaTargetType.Native)

    li(`class` := "targets")(
      fieldset(
        legend("Target"),
        ul(targets.map(target =>
          li(
            label(
              input.radio(
                checked := scalaTarget.targetType == target,
                onChange ==> backend.setTarget(target.scalaTarget),
                name := "target"
              ),
              img(src := s"/assets/${target.logoFileName}", alt := s"logo for ${target.label}"),
              span(target.label)
            )
          )
        ))
      )
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
        li(`class` := "versions")(
          fieldset(
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
        )
      case ScalaTarget.Js(scalaVersion, scalaJsVersion) => EmptyTag
      case ScalaTarget.Dotty => EmptyTag
      // case ScalaTarget.Native => EmptyTag
    }
  }

  private val component = ReactComponentB[(State, Backend)]("Settings")
    .render_P { case (state, backend) =>
      ul(`class` := "settings")(
        renderTarget(state.inputs.target, backend),
        renderVersions(state.inputs.target, backend),
        ScaladexSearch(state, backend)
      )
    }
    .build
  def apply(state: State, backend: Backend) = component((state, backend))
}