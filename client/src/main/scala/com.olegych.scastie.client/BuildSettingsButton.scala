package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._

object BuildSettingsButton {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("BuildSettingsButton").render_P {
      case (state, backend) =>
        import backend._

        def selected =
          if (state.view == View.BuildSettings) TagMod(`class` := "selected")
          else EmptyTag

        li(onClick ==> setView2(View.BuildSettings),
           title := "Open Build Settings",
           selected,
           `class` := "btn")(
          i(`class` := "fa fa-gear"),
          span("Build Settings")
        )
    }.build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
