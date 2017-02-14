package com.olegych.scastie.client

import App._

import japgolly.scalajs.react._, vdom.all._

object ClearButton {

  def apply(state: State, backend: Backend) = component((state, backend))

  private val component =
    ReactComponentB[(State, Backend)]("ClearButton").render_P {
      case (state, backend) =>
        def disabled(isDisabled: Boolean) =
          if(isDisabled) "disabled"
          else ""

        li( onClick ==> backend.clear,
            title := "Clear Instrumentations (Esc)",
           `class` := s"button ${disabled(state.isClearable)}")(
          i(`class` := "fa fa-eraser"),
          p("Clear")
        )
    }.build
}
