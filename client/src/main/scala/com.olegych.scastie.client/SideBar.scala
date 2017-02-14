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

        val instrumentationSelected =
          if (state.inputs.isInstrumented) TagMod(`class` := "toggle selected")
          else EmptyTag

        def openInNewTab(link: String): Callback = {
          Callback(
            dom.window.open(link, "_blank").focus()
          )
        }

        def feedback(e: ReactEventI): Callback = 
          openInNewTab("https://gitter.im/scalacenter/scastie")

        def issue(e: ReactEventI): Callback = 
          openInNewTab("https://github.com/scalacenter/scastie/issues/new")

        nav(`class` := s"sidebar $theme")(
          ul(
            RunButton(state, backend),
            ClearButton(state, backend),
            li(onClick ==> save, title := "Save", `class` := "button")(
              i(`class` := "fa fa-floppy-o"),
              p("Save")
            ),
            li( onClick ==> setView2(View.Libraries),
                title := "Open Libraries View",
               `class` := "button",
                selected(View.Libraries))(

              img(src := "/assets/public/dotty3.svg",
                  alt := "settings",
                  `class` := "libraries-button"),
              p("Libraries")
            ),
            li(`class` := "button", onClick ==> formatCode)(
              iconic.justifyLeft,
              p("Format")
            ),
            li(`class` := "button",
               instrumentationSelected,
               onClick ==> toggleInstrumentation)(
              iconic.script,
              p("Script")
            ),
            li(`class` := "button",
               consoleSelected,
               onClick ==> toggleConsole)(
              iconic.terminal,
              p("Console")
            ),
            li(`class` := "button",
               onClick ==> feedback)(
              iconic.chat,
              p("Feedback")
            ),
            li(`class` := "button",
               onClick ==> issue)(
              iconic.bug,
              p("Issue")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) =
    component((state, backend))
}
