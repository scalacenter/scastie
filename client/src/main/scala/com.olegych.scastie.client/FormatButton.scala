package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object FormatButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("FormatButton").render_P {
      case (state, backend) =>

      li(
        title := "Format Code (F6)",
        `class` := "btn", onClick ==> backend.formatCode)(
        i(`class` := "fa fa-align-left"),
        span("Format")
      )
    }.build
}
