package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

import iconic._

object RunButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("RunButton").render_P {
      case (state, backend) =>
        import backend._
        import View.ctrl

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        if (!state.running) {
          if (View.Editor == state.view) {
            li(onClick ==> run,
               title := s"Run Code ($ctrl + Enter)",
               `class` := "button run-button",
               selected(View.Editor))(
              mediaPlay(`class` := "runnable"),
              p("Run")
            )
          } else {
            li(onClick ==> setView2(View.Editor),
               title := "Open Edit View",
               `class` := "button run-button",
               selected(View.Editor))(
              pencil,
              p("Edit")
            )
          }
        } else {
          li(onClick ==> setView2(View.Editor),
             title := "Open Edit View",
             `class` := "button run-button")(
            div(`class` := "sk-folding-cube-wraper")(
              div(`class` := "sk-folding-cube")(
                div(`class` := "sk-cube1 sk-cube"),
                div(`class` := "sk-cube2 sk-cube"),
                div(`class` := "sk-cube4 sk-cube"),
                div(`class` := "sk-cube3 sk-cube")
              )
            ),
            p("Running")
          )
        }
    }.build
}
