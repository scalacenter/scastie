package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

object RunButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("RunButton").render_P {
      case (state, backend) =>
        import backend._
        import View.ctrl

        if (!state.isRunning) {
          li(onClick ==> run,
             title := s"Run Code ($ctrl + Enter)",
             `class` := "btn run-button")(
            i(`class` := "fa fa-play"),
            span("Run")
          )
        } else {
          li(onClick ==> setView2(View.Editor),
             title := "Running your Code...",
             `class` := "btn run-button")(
            i(`class` := "fa fa-spinner fa-spin"),
            span("Running")
          )
        }
    }.build
}
