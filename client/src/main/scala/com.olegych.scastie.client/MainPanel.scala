package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MainPanel {
  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend, AppProps)]("MainPanel")
      .render_P {
        case (state, backend, props) => {
          def show(view: View) = {
            if (view == state.view) TagMod(display.block)
            else TagMod(display.none)
          }

          val embedded = props.embedded.isDefined

          val embeddedMenu =
            if (embedded) TagMod(EmbeddedMenu(state, backend))
            else EmptyVdom

          val consoleCssForEditor =
            if (state.consoleState.consoleIsOpen)
              TagMod(`class` := "console-open")
            else EmptyVdom

          val snippets =
            if(state.user.isDefined) {
              div(`class` := "snippets-container",
                  `class` := "inner-container",
                  show(View.CodeSnippets))(
                CodeSnippets(props.router, state, backend)
              )
            }
            else EmptyVdom

          div(`class` := "main-panel")(
            TopBar(state, backend),
            EditorTopBar(state, backend, props.snippetId),
            div(`class` := "content")(
              div(`class` := "editor-container",
                  `class` := "inner-container",
                  show(View.Editor))(
                div(`class` := "code", consoleCssForEditor)(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(`class` := "settings-container",
                  `class` := "inner-container",
                  show(View.BuildSettings))(
                BuildSettings(state, backend)
              ),
              snippets,
              MobileBar(state, backend)
            )
          )
        }
      }
      .build

  def apply(state: AppState, backend: AppBackend, props: AppProps) =
    component((state, backend, props))
}
