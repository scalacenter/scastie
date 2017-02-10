package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._, vdom.all._

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
            RunButton(state, backend),
            li(`class` := "button", onClick ==> save)(
              i(`class` :="fa fa-floppy-o"),
              p("Save")
            ),
            li(`class` := "button", selected(View.Settings), onClick ==> setView(View.Settings))(
              img(src := "/assets/dotty3.svg",
                  alt := "settings",
                  `class` := "libraries"),
              p("Libraries")
            ),
            li(`class` := "button", onClick ==> formatCode)(
              iconic.justifyLeft,
              p("Format")
            ),
            li(`class` := "button", instrumentationSelected, onClick ==> toggleInstrumentation)(
              iconic.script,
              p("Script")
            ),
            li(`class` := "button", consoleSelected, onClick ==> toggleConsole)(
              iconic.terminal,
              p("Console")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) =
    component((state, backend))
}
