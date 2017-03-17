package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._

object LibraryButton {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("BuildSettingsButton").render_P {
      case (state, backend) =>
        import backend._

        def selected =
          if (state.view == View.Libraries) TagMod(`class` := "selected")
          else EmptyTag

        li(onClick ==> setView2(View.Libraries),
           title := "Open Build Settings",
           selected,
           `class` := "btn")(
          i(`class` := "fa fa-gear"),
          "Build Settings"
        )
    }.build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
