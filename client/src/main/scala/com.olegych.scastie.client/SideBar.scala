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

        val consoleLabel =
          if (state.consoleIsOpen) "Close"
          else "Open"

        val saveDisabled =
          if(state.inputsHasChanged) "disabled"
          else ""

        import View.ctrl

        nav(`class` := s"sidebar $theme")(
          ul(
            li(onClick ==> resetAll,
               title := "Scastie Logo",
               `class` := "logo")(
              img(src := "/assets/public/dotty3.svg",
                  alt := "Logo")
            ),
            RunButton(state, backend),
            ClearButton(state, backend),
            li(onClick ==> setView2(View.Libraries),
               title := "Open Libraries View",
               selected(View.Libraries),
               `class` := "button")(
              img(src := "/assets/public/dotty3.svg",
                  alt := "Libraries (Build)",
                  `class` := "image-button"),
              p("Libraries (Build)")
            ),
            li(onClick ==> save,
               title := s"Save ($ctrl + S)",
               `class` := s"button $saveDisabled")(
              i(`class` := "fa fa-floppy-o"),
              p("Save")
            ),
            li(onClick ==> formatCode,
               title := "Format Code (F6)",
               `class` := "button")(
              iconic.justifyLeft,
              p("Format")
            ),
            li(onClick ==> toggleConsole,
               title := s"$consoleLabel Console",
               consoleSelected,
               `class` := "button")(
              iconic.terminal,
              p("Console")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) =
    component((state, backend))
}
