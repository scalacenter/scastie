package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object NewButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("NewButton").render_P {
      case (state, backend) =>
        li(title := "New code snippet",
           `class` := "btn",
           onClick ==> backend.newSnippet)(
          i(`class` := "fa fa-file-o"),
          span("New")
        )
    }.build
}
