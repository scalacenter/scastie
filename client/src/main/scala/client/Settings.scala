package client

import App._

import japgolly.scalajs.react._, vdom.all._

object Settings {
  private val component = ReactComponentB[(State, Backend)]("Settings")
    .render_P { case (state, backend) =>
      div(`class` := "settings")(
        TargetSelection(state, backend),
        ScaladexSearch(state, backend)
      )
    }
    .build
  def apply(state: State, backend: Backend) = component((state, backend))
}

object TargetSelection {
  private sealed trait ScalaTargetType {
    def logoFileName: String
    def label: String
    def scalaTarget: ScalaTarget
  }
  private case object JVM extends ScalaTargetType {
    def logoFileName = "smooth-spiral.png"
    def label = "Scala"
    def scalaTarget = ScalaTarget.Jvm()
  }
  private case object Dotty extends ScalaTargetType {
    def logoFileName = "dotty3.svg"
    def label = "Dotty"
    def scalaTarget = ScalaTarget.Dotty
  }
  private case object JS extends ScalaTargetType {
    def logoFileName = "scala-js.svg"
    def label = "Scala.Js"
    def scalaTarget = ScalaTarget.Js()
  }
  // private case object Native extends ScalaTargetType {
  //   def logoFileName = "native2.png"
  //   def label = "Native"
  //   def scalaTarget = ScalaTarget.Native
  // }

  private val component = ReactComponentB[(State, Backend)]("Target Selection")
    .render_P { case (state, backend) =>
      import backend._

      val suggestedVersions = List(
        ("old", Version(2, 10, 6)),
        ("latest", Version(2, 11, 8)),
        ("next", Version(2, 12, 0, "-RC1"))
      )

      val versionSelection = {
        state.inputs.target match {
          case ScalaTarget.Jvm(scalaVersion) => {
            fieldset(
              legend("Scala Version"),
              ul(suggestedVersions.map{ case (versionStatus, version) =>
                li(
                  label(
                    input.radio(
                      checked := scalaVersion == version,
                      onClick ==> setTarget(ScalaTarget.Jvm(version)),
                      name := "version"
                    ),
                    span(s"${version.binary} ($versionStatus)")
                  )
                )
              })
            )
          }
          case ScalaTarget.Dotty => EmptyTag
          // case ScalaTarget.Native => EmptyTag
          case ScalaTarget.Js(scalaVersion, scalaJsVersion) => EmptyTag
        }
      }

      val targets = List(JVM, Dotty, JS) //, Native)

      def isSeleted(targetType: ScalaTargetType): Boolean = {
        val stateTargetType = 
          state.inputs.target match {
            case _ : ScalaTarget.Jvm => JVM
            case ScalaTarget.Dotty => Dotty
            // case ScalaTarget.Native => Native
            case _ : ScalaTarget.Js => JS
          }

        targetType == stateTargetType
      }

      ul(
        li(`class` := "targets")(
          fieldset(
            legend("Target"),
            ul(targets.map(target =>
              li(
                label(
                  input.radio(
                    checked := isSeleted(target),
                    onClick ==> setTarget(target.scalaTarget),
                    name := "target"
                  ),
                  img(src := s"/assets/${target.logoFileName}", alt := s"logo for ${target.label}"),
                  span(target.label)
                )
              )
            ))
          )
        ),
        li(`class` := "versions")(
          versionSelection
        )
      )
    }
    .build
  def apply(state: State, backend: Backend) = component((state, backend))
}