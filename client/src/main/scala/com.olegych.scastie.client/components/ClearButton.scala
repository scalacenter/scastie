package com.olegych.scastie.client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object ClearButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("ClearButton")
      .render_P {
        case (state, backend) =>
          li(title := "Clear Instrumentations (Esc)",
             role := "button",
             clazz := "btn",
             onClick --> backend.clear)(
            div(
              i(clazz := "fa fa-eraser"),
              span("Clear")
            )
          )
      }
      .build
}
