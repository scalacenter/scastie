package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MobileBar {
  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("MobileBar").render_P {
      case (state, backend) =>

        // val viewButton = 
        //   if (state.view == View.BuildSettings)
        //   else

        nav(`id` := "editor-mobile")(
          ul(`class` := "editor-buttons")(
            RunButton(state, backend)//,
            // viewButton
          )
        )
    }.build

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))
}
