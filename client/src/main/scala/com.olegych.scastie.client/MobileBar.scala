package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MobileBar {
  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("MobileBar")
      .render_P {
        case (state, backend) =>
          nav(`class` := "editor-mobile")(
            ul(`class` := "editor-buttons")(
              RunButton(state, backend)
            )
          )
      }
      .build

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))
}
