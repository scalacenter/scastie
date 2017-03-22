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

        val isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled") else TagMod(onClick ==> backend.clear)

        li(
           title := "Clear Instrumentations (Esc)",
           `class` := "btn", isDisabled)(
          i(`class` := "fa fa-eraser"),
          "Clear"
        )
    }.build
}
