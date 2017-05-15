package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

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
              TagMod(clazz := "console-open")
            else EmptyVdom

          val snippets =
            if (state.user.isDefined) {
              div(clazz := "snippets-container",
                  clazz := "inner-container",
                  show(View.CodeSnippets))(
                CodeSnippets(props.router, state, backend)
              )
            } else EmptyVdom

          div(clazz := "main-panel")(
            // pre(clazz := "debug")(),
            TopBar(state, backend),
            EditorTopBar(state, backend, props.snippetId),
            div(clazz := "content")(
              div(clazz := "editor-container",
                  clazz := "inner-container",
                  show(View.Editor))(
                div(clazz := "code", consoleCssForEditor)(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(clazz := "settings-container",
                  clazz := "inner-container",
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
