package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MobileBar {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("MobileBar").render_P {
      case (state, backend) =>

        nav(`id` := "editor-mobile")(
          ul(`class` := "editor-buttons")(
            RunButton(state, backend),
            DesktopButton(state, backend)
          )
        )
    }.build

  def apply(state: AppState,
            backend: AppBackend) =
    component((state, backend))
}
