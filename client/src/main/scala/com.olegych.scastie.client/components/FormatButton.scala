package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object FormatButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("FormatButton")
      .render_P {
        case (state, backend) =>
          val disabledIfSameInputs =
            if (!state.inputsHasChanged) "disabled"
            else ""

          li(title := "Format Code (F6)",
             role := "button",
             clazz := s"btn $disabledIfSameInputs",
             onClick --> backend.formatCode)(
            i(clazz := "fa fa-align-left"),
            span("Format")
          )
      }
      .build
}
