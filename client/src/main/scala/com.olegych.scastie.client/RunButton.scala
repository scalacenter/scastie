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

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        if (!state.running) {
          if (View.Editor == state.view) {
            // RUN
            li(`class` := "button run-button",
               selected(View.Editor),
               onClick ==> run)(
              mediaPlay(`class` := "runnable"),
              p("Run")
            )
          } else {
            li(`class` := "button run-button",
               selected(View.Editor),
               onClick ==> setView(View.Editor))(
              pencil(title := "Edit code"),
              p("Edit")
            )
          }
        } else {
          li(`class` := "button run-button")(
            div(`class` := "sk-folding-cube",
                onClick ==> setView(View.Editor))(
              div(`class` := "sk-cube1 sk-cube"),
              div(`class` := "sk-cube2 sk-cube"),
              div(`class` := "sk-cube4 sk-cube"),
              div(`class` := "sk-cube3 sk-cube")
            ),
            p("Running")
          )
        }
    }.build
}
