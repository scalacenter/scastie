package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object EmbeddedMenu {
  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("RunButton")
      .render_P {
        case (state, backend) =>
          div(clazz := "embedded-menu")(
            RunButton(state, backend),
            ClearButton(state, backend)
          )
      }
      .build
}
