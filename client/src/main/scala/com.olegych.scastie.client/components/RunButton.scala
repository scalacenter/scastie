package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object RunButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("RunButton")
      .render_P {
        case (state, backend) =>
          import backend._
          import View.ctrl

          if (!state.isRunning) {
            li(onClick ==> run,
               role := "button",
               title := s"Run Code ($ctrl + Enter)",
               clazz := "btn run-button")(
              i(clazz := "fa fa-play"),
              span("Run")
            )
          } else {
            li(onClick ==> setView2(View.Editor),
               title := "Running your Code...",
               clazz := "btn run-button")(
              i(clazz := "fa fa-spinner fa-spin"),
              span("Running")
            )
          }
      }
      .build
}
