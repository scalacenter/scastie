package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MainPanel {
  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend, AppProps)]("MainPanel")
      .render_P {
        case (state, backend, props) =>

          def show(view: View) =
            if (view == state.view) display.block
            else display.none

          val embedded = props.embedded.isDefined

          val embeddedMenu =
            EmbeddedMenu(state, backend).when(embedded)

          val consoleCssForEditor =
            (cls := "console-open").when(state.consoleState.consoleIsOpen)

          val snippets =
            div(cls := "snippets-container",
                cls := "inner-container",
                show(View.CodeSnippets))(
              CodeSnippets(props.router, state, backend)
            ).when(state.user.isDefined)

          div(cls := "main-panel",
            // pre(cls := "debug")(),
            TopBar(backend.viewSnapshot(state.view), state.user).render,
            EditorTopBar(state, backend, props.snippetId),
            div(cls := "content",
              div(cls := "editor-container",
                  cls := "inner-container",
                  show(View.Editor))(
                div(cls := "code", consoleCssForEditor)(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(cls := "settings-container",
                  cls := "inner-container",
                  show(View.BuildSettings))(
                BuildSettings(state, backend)
              ),
              snippets,
              MobileBar(state, backend)
            )
          )
      }
      .build

  def apply(state: AppState, backend: AppBackend, props: AppProps) =
    component((state, backend, props))
}
