package com.olegych.scastie
package client

import com.olegych.scastie.client.DefaultSizes._
import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.window._

object SideBar {

  private val component =
    ReactComponentB[(AppState, AppBackend, Option[SnippetId])]("SideBar").render_P {
      case (state, backend, snippetId) =>
        import backend._

        def currentView = state.view

        def isDisabled(tagMod: TagMod) =
          if (currentView != View.Editor) TagMod(`class` := "disabled") else tagMod

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
                li(
                   title := s"Save ($ctrl + S)",
                   `class` := "btn", isDisabled(onClick ==> save))(
                  i(`class` := "fa fa-download"),
                  "Save"
                )
              )
            case Some(sid) =>
              TagMod(
                li(
                  title := s"Amend",
                  `class` := "btn", isDisabled(onClick ==> amend(sid)))(
                  i(`class` := "fa fa-pencil-square-o"),
                  "Amend"
                ),
                li(
                   title := s"Save ($ctrl + S)",
                   `class` := "btn", isDisabled(onClick ==> update(sid)))(
                  i(`class` := "fa fa-download"),
                  "Update"
                ),
                li(
                   title := s"Fork",
                   `class` := "btn", isDisabled(onClick ==> fork(sid)))(
                  i(`class` := "fa fa-code-fork"),
                  "Fork"
                )
              )
          }

        val formatCodeButton =
          li(
            title := "Format Code (F6)",
            `class` := "btn", isDisabled(onClick ==> formatCode))(
            i(`class` := "fa fa-align-left"),
            "Format"
          )

        val themeButton =
          li(onClick ==> toggleTheme,
            title := s"Select $toggleThemeLabel Theme (F2)",
            `class` := "btn")(
            i(`class` := s"fa $selectedIcon"),
            toggleThemeLabel
          )

        val helpButton =
          li(onClick ==> toggleHelp,
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
              BuildSettingsButton(state, backend)
            )
          case View.BuildSettings =>
            Seq(
              RunButton(state, backend),
              formatCodeButton,
              ClearButton(state, backend),
              WorksheetButton(state, backend),
              sharing,
              BuildSettingsButton(state, backend)
            )
          case View.CodeSnippets =>
            Seq(
              RunButton(state, backend),
              formatCodeButton,
              ClearButton(state, backend),
              WorksheetButton(state, backend),
              sharing,
              BuildSettingsButton(state, backend)
            )
        }

        val currentButtonsForSelectedView = buttonsRibbon(currentView)

        val buttonsBottom: Seq[TagMod] = Seq(themeButton, helpButton)

        def actionsContainerStyle: TagMod = TagMod(
          height := s"${if (innerHeight < sideBarMinHeight) sideBarMinHeight else innerHeight}px")

        nav(`id` := "sidebar")(
          div(`class` := "actions-container", actionsContainerStyle)(
            a(`class` := "logo", href := "#")(
              img(src := "/assets/public/img/icon-scastie.png"),
              h1("Scastie")
            ),
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
