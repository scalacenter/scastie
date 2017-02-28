package com.olegych.scastie
package client

import App._
import api.SnippetId

import japgolly.scalajs.react._, vdom.all._

object SideBar {
  private val component =
    ReactComponentB[(State, Backend, Option[SnippetId])]("SideBar").render_P {
      case (state, backend, snippetId) =>
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

        val disabledIfSameInputs =
          if(!state.inputsHasChanged) "disabled"
          else ""

        import View.ctrl

        val sharing =
          snippetId match {
            case None =>
              TagMod(
                li(onClick ==> save,
                   title := s"Save ($ctrl + S)",
                   `class` := s"button $disabledIfSameInputs")(
                  i(`class` := "fa fa-floppy-o"),
                  p("Save")
                )
              )
            case Some(sid) =>
              TagMod(
                li(onClick ==> update(sid),
                   title := s"Update ($ctrl + S)",
                   `class` := "button")(

                  i(`class` := "fa fa-floppy-o"),
                  p("Update")
                ),
                li(onClick ==> fork(sid),
                   title := s"Fork",
                   `class` := "button")(

                  i(`class` := "fa fa-code-fork"),
                  p("Fork")
                ),
                li(onClick ==> amend(sid),
                   title := s"Amend",
                   `class` := "button")(

                  i(`class` := "fa fa-pencil-square-o"),
                  p("Amend")
                )
              )
          }

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
            li(onClick ==> formatCode,
               title := "Format Code (F6)",
               `class` := s"button $disabledIfSameInputs")(
              iconic.justifyLeft,
              p("Format")
            ),
            li(onClick ==> toggleConsole,
               title := s"$consoleLabel Console",
               consoleSelected,
               `class` := "button")(
              iconic.terminal,
              p("Console")
            ),
            sharing
          )
        )
    }.build

  def apply(state: State, backend: Backend, snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
