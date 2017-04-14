package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

object EmbeddedMenu {
  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("RunButton").render_P {
      case (state, backend) =>
        div(`class` := "embedded-menu")(
          RunButton(state, backend),
          ClearButton(state, backend)
        )
    }.build
}
