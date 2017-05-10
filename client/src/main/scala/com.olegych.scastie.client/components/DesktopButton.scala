package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object DesktopButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("DesktopButton").render_P {
      case (state, backend) =>
        li(title := "Go to desktop",
           clazz := "btn",
           onClick ==> backend.forceDesktop)(
          i(clazz := "fa fa-desktop"),
          span("Desktop")
        )
    }.build
}
