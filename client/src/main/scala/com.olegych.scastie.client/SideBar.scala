package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom

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
          if(state.consoleIsOpen) "Close"
          else "Open"

        val worksheetModeSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "toggle selected")
          else EmptyTag

        val worksheetModeToogleLabel =
          if (state.inputs.worksheetMode) "OFF"
          else "ON"

        def openInNewTab(link: String): Callback = {
          Callback(
            dom.window.open(link, "_blank").focus()
          )
        }

        def feedback(e: ReactEventI): Callback = 
          openInNewTab("https://gitter.im/scalacenter/scastie")

        def issue(e: ReactEventI): Callback = 
          openInNewTab("https://github.com/scalacenter/scastie/issues/new")

        import View.ctrl

        nav(`class` := s"sidebar $theme")(
          ul(
            RunButton(state, backend),
            ClearButton(state, backend),
            li( onClick ==> setView2(View.Libraries),
                title := "Open Libraries View",
                selected(View.Libraries),
               `class` := "button")(

              img(src := "/assets/public/dotty3.svg",
                  alt := "settings",
                  `class` := "libraries-button"),
              p("Libraries (Build)")
            ),
            li( onClick ==> formatCode,
                title := "Format Code (F6)",
               `class` := "button")(
              iconic.justifyLeft,
              p("Format")
            ),
            li(onClick ==> save, title := s"Save ($ctrl + S)", `class` := "button")(
              i(`class` := "fa fa-floppy-o"),
              p("Save")
            ),
            li( onClick ==> toggleConsole,
                title := s"$consoleLabel Console",
                consoleSelected,
               `class` := "button"
               )(
              iconic.terminal,
              p("Console")
            ),
            li( onClick ==> feedback,
                title := "Open Gitter.im Chat to give us feedback",
               `class` := "button"
               )(
              iconic.chat,
              p("Feedback")
            ),
            li(onClick ==> issue,
               title := "Create new issue on GitHub",
               `class` := "button"
               )(
              iconic.bug,
              p("Issue")
            ),
            li( onClick ==> toggleWorksheetMode,
                title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
                worksheetModeSelected,
               `class` := "button"
               )(
              iconic.script,
              p("Worksheet")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) =
    component((state, backend))
}
