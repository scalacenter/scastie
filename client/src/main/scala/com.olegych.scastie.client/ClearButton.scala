package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

object ClearButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("ClearButton").render_P {
      case (state, backend) =>

        li(
           title := "Clear Instrumentations (Esc)",
           `class` := "btn", onClick ==> backend.clear)(
          i(`class` := "fa fa-eraser"),
          "Clear"
        )
    }.build
}
