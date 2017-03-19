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
               `class` := "btn selected run-button",
               selected(View.Editor))(
              mediaPlay(`class` := "runnable"),
              "Run"
            )
          } else {
            li(onClick ==> setView2(View.Editor),
               title := "Open Edit View",
               `class` := "btn selected run-button",
               selected(View.Editor))(
              i(`class` := "fa fa-edit"),
              "Edit"
            )
          }
        } else {
          li(onClick ==> setView2(View.Editor),
             title := "Open Edit View",
             `class` := "btn selected run-button")(
            i(`class` := "fa fa-spinner"),
            "Running"
          )
        }
    }.build
}
