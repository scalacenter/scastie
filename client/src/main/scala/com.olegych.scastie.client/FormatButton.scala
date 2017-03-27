package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object FormatButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("FormatButton").render_P {
      case (state, backend) =>

        val isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled") else TagMod(onClick ==> backend.formatCode)

      li(
        title := "Format Code (F6)",
        `class` := "btn", isDisabled)(
        i(`class` := "fa fa-align-left"),
        "Format"
      )
    }.build
}
