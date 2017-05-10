package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object MobileBar {
  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("MobileBar")
      .render_P {
        case (state, backend) =>
          nav(clazz := "editor-mobile")(
            ul(clazz := "editor-buttons")(
              RunButton(state, backend),
              DesktopButton(state, backend)
            )
          )
      }
      .build

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))
}
