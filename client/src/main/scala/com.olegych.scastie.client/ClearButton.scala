package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

object ClearButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("ClearButton").render_P {
      case (state, backend) =>
        def disabled(isDisabled: Boolean) =
          if (isDisabled) "disabled"
          else ""

        li(onClick ==> backend.clear,
           title := "Clear Instrumentations (Esc)",
           `class` := "btn")(
          i(`class` := "fa fa-eraser"),
          "Clear"
        )
    }.build
}
