package com.olegych.scastie.client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

object RunButton {

  def apply(state: State, backend: Backend) = component((state, backend))

  private val component =
    ReactComponentB[(State, Backend)]("RunButton").render_P {
      case (state, backend) =>
        import backend._

        val runButton = if (state.running) {
          TagMod(
            div(`class` := "sk-folding-cube",
                onClick ==> setView(View.Editor))(
              div(`class` := "sk-cube1 sk-cube"),
              div(`class` := "sk-cube2 sk-cube"),
              div(`class` := "sk-cube4 sk-cube"),
              div(`class` := "sk-cube3 sk-cube")
            ),
            p("Running")
          )
        } else {
          if (View.Editor == state.view) {
            // RUN
            TagMod(
              mediaPlay(
                onClick ==> run,
                `class` := "runnable"
              ),
              p("Run")
            )
          } else {
            TagMod(
              pencil(
                onClick ==> setView(View.Editor),
                title := "Edit code"
              ),
              p("Edit")
            )
          }
        }
        div(`class` := "button run-button")(runButton)
    }.build

}
