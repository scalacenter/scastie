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

        val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

        val selectedIcon =
          if (state.isDarkTheme) "fa fa-sun-o"
          else "fa fa-moon-o"

        val disabledIfSameInputs =
          if (!state.inputsHasChanged) "disabled"
          else ""
        import View.ctrl

        val sharing =
          snippetId match {
            case None =>
              TagMod(
                li(onClick ==> save,
                   title := s"Save ($ctrl + S)",
                   `class` := "btn")(
                  i(`class` := "fa fa-download"),
                  "Save"
                )
              )
            case Some(sid) =>
              TagMod(
                li(onClick ==> update(sid),
                   title := s"Save ($ctrl + S)",
                   `class` := "btn")(
                  i(`class` := "fa fa-pencil-square-o"),
                  "Update"
                ),
                li(onClick ==> fork(sid),
                   title := s"Fork",
                   `class` := "btn")(
                  i(`class` := "fa fa-code-fork"),
                  "Fork"
                ),
                li(onClick ==> amend(sid),
                   title := s"Share",
                   `class` := "btn")(
                  i(`class` := "fa fa-share-alt"),
                  "Share"
                )
              )
          }

        val formatCodeButton =
          li(onClick ==> formatCode,
            title := "Format Code (F6)",
            `class` := "btn")(
            i(`class` := "fa fa-align-left"),
            "Format"
          )

        val themeButton =
          li(onClick ==> backend.toggleTheme,
            title := s"Select $toggleThemeLabel Theme (F2)",
            `class` := "btn")(
            i(`class` := s"fa $selectedIcon"),
            toggleThemeLabel
          )

        val helpButton =
          li(onClick ==> showHelp,
            title := "Show help Menu",
            `class` := "btn")(
            i(`class` := "fa fa-question-circle"),
            "Help"
          )

        def buttonsRibbon: View => Seq[TagMod] = {
          case View.Editor =>
            Seq(
              RunButton(state, backend),
              formatCodeButton,
              ClearButton(state, backend),
              WorksheetButton(state, backend),
              sharing,
              LibraryButton(state, backend)
            )
          case View.Libraries =>
            Seq(
              RunButton(state, backend),
              LibraryButton(state, backend)
            )
          case View.UserProfile =>
            Seq(
              RunButton(state, backend),
              LibraryButton(state, backend)
            )
        }

        val currentButtonsForSelectedView = buttonsRibbon(currentView)

        val buttonsBottom: Seq[TagMod] =
            Seq(
              themeButton,
              helpButton
            )

        nav(`id` := s"sidebar")(
          a(`class` := "logo", href := "#",
            img(src := "/assets/public/img/icon-scastie.png"),
            h1("Scastie")
          ),
          div(`class` := "actions-container", height := "1000px")(
            ul(`class` := "actions")(
              currentButtonsForSelectedView
            ),
            ul(`class` := "actions bottom")(
              buttonsBottom
            )
          )
        )

    }.build

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
