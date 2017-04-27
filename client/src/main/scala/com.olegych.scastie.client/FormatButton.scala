package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object FormatButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("FormatButton").render_P {
      case (state, backend) =>
        val disabledIfSameInputs =
          if (!state.inputsHasChanged) "disabled"
          else ""

        li(title := "Format Code (F6)",
           role := "button",
           `class` := s"btn $disabledIfSameInputs",
           onClick ==> backend.formatCode)(
          i(`class` := "fa fa-align-left"),
          span("Format")
        )
    }.build
}
