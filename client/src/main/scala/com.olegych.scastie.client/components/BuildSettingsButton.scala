package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object BuildSettingsButton {

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("BuildSettingsButton")
      .render_P {
        case (state, backend) =>
          import backend._

          def selected =
            if (state.view == View.BuildSettings) TagMod(clazz := "selected")
            else EmptyVdom

          li(onClick ==> setView2(View.BuildSettings),
             role := "button",
             title := "Open Build Settings",
             selected,
             clazz := "btn")(
            i(clazz := "fa fa-gear"),
            span("Build Settings")
          )
      }
      .build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
