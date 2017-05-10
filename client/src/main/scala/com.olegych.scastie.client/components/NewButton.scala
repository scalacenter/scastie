package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object NewButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("NewButton")
      .render_P {
        case (state, backend) =>
          li(title := "New code snippet",
             role := "button",
             clazz := "btn",
             onClick ==> backend.newSnippet)(
            i(clazz := "fa fa-file-o"),
            span("New")
          )
      }
      .build
}
