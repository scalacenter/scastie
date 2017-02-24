package com.olegych.scastie.client

import App._

import japgolly.scalajs.react._, vdom.all._

object UserProfile {

  def apply(state: State, backend: Backend) = component((state, backend))

  private val component =
    ReactComponentB[(State, Backend)]("UserProfile").render_P {
      case (state, backend) =>

        val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

        val selectedTheme =
          if (state.isDarkTheme) iconic.sun
          else iconic.moon

        div(`class` := "profile")(
          button(onClick ==> backend.toggleTheme,
             title := s"Select $toggleThemeLabel Theme (F2)",
             `class` := "button")(
            selectedTheme,
            p("Theme")
          )
        )
    }.build
}
