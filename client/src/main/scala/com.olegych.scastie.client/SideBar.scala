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

        val formatCodeButton = li(onClick ==> formatCode,
                                  title := "Format Code (F6)",
                                  `class` := "btn")(
          i(`class` := "fa fa-align-left"),
          "Format"
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

        nav(`id` := s"sidebar")(
          a(`class` := "logo", href := "#",
            img(src := "/assets/public/img/icon-scastie.png"),
            h1("Scastie")
          ),
          div(`class` := "actions-container")(
            ul(`class` := "actions")(
              currentButtonsForSelectedView
            )
          )
        )

    }.build

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
