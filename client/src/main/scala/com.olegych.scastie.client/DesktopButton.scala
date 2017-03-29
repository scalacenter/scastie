package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

object DesktopButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("DesktopButton").render_P {
      case (state, backend) =>

        li(
           title := "Go to desktop",
           `class` := "btn", onClick ==> backend.toggleForcedDesktop2(value = true))(
          i(`class` := "fa fa-desktop"),
          span("Desktop")
        )
    }.build
}
