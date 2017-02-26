package com.olegych.scastie.client

import App._

import japgolly.scalajs.react._, vdom.all._

object UserProfile {

  def apply(state: State, backend: Backend) = component((state, backend))

  private val component =
    ReactComponentB[(State, Backend)]("UserProfile").render_P {
      case (state, backend) =>


        div(`class` := "profile")(
          
        )
    }.build
}
