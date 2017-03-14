package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._

object LibraryButton {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("LibraryButton").render_P {
      case (state, backend) =>
        import backend._

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        def isSelected = View.Libraries == state.view

        def getButtonImage = if (isSelected) "library.svg" else "library-gray.svg"

        li(onClick ==> setView2(View.Libraries),
          title := "Open Libraries View",
          selected(View.Libraries),
          `class` := "button library-button")(
          img(src := s"""/assets/public/$getButtonImage""",
            alt := "Libraries (Build)",
            `class` := "image-button"),
          p("Libraries (Build)")
        )
    }
    .build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}