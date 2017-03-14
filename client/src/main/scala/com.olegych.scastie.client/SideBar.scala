package com.olegych.scastie
package client

import api.SnippetId

import japgolly.scalajs.react._, vdom.all._

object SideBar {
  private val component =
    ReactComponentB[(AppState, AppBackend, Option[SnippetId])]("SideBar").render_P {
      case (state, backend, snippetId) =>
        import backend._

        val theme = if (state.isDarkTheme) "dark" else "light"

        def selected(view: View) =
          if (view == currentView) TagMod(`class` := "selected") else EmptyTag

        def currentView = state.view

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
                   title := s"Save ($ctrl + S)",
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

        val formatCodeButton = li(onClick ==> formatCode,
          title := "Format Code (F6)",
          `class` := s"button $disabledIfSameInputs")(
          iconic.justifyLeft,
          p("Format")
        )

        val console = li(onClick ==> toggleConsole,
          title := s"$consoleLabel Console",
          consoleSelected,
          `class` := "button")(
          iconic.terminal,
          p("Console")
        )

        def buttonsRibbon: View => Seq[TagMod] = {
          case View.Editor => Seq(
            LibraryButton(state, backend),
            RunButton(state, backend),
            ClearButton(state, backend),
            formatCodeButton,
            console,
            sharing
          )
          case View.Libraries => Seq(
            LibraryButton(state, backend),
            RunButton(state, backend)
          )
          case View.UserProfile => Seq(
            LibraryButton(state, backend),
            RunButton(state, backend),
            li("User Profile (NY)")
          )
        }

        val currentButtonsForSelectedView = buttonsRibbon(currentView)

        nav(`class` := s"sidebar $theme")(
          ul(
            li(onClick ==> goHome,
               title := "Scastie Logo",
               `class` := "logo")(
              img(src := "/assets/public/dotty3.svg",
                  alt := "Logo")
            ),
            currentButtonsForSelectedView
          )
        )

    }.build

  def apply(state: AppState, backend: AppBackend, snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
