package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

object SideBar {
  private val component =
    ReactComponentB[(State, Backend)]("SideBar").render_P {
      case (state, backend) =>
        import backend._

        val theme = if (state.isDarkTheme) "dark" else "light"

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        val consoleSelected =
          if (state.consoleIsOpen) TagMod(`class` := "toggle selected")
          else EmptyTag

        val instrumentationSelected =
          if (state.inputs.isInstrumented) TagMod(`class` := "toggle selected")
          else EmptyTag

        nav(`class` := s"sidebar $theme")(
          ul(
            li(`class` := "button", selected(View.Editor))(
              RunButton(state, backend)
            ),
            li(`class` := "button")(
              iconic.share(onClick ==> save),
              p("Share")
            ),
            li(`class` := "button", selected(View.Settings))(
              img(src := "/assets/dotty3.svg",
                  alt := "settings",
                  `class` := "libraries",
                  onClick ==> setView(View.Settings)),
              p("Libraries")
            ),
            li(`class` := "button")(
              iconic.justifyLeft(onClick ==> formatCode),
              p("Format")
            ),
            li(`class` := "button", instrumentationSelected)(
              iconic.script(onClick ==> toggleInstrumentation),
              p("Script")
            ),
            li(`class` := "button", consoleSelected)(
              terminal(onClick ==> toggleConsole),
              p("Console")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) =
    component((state, backend))
}
