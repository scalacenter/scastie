package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object MainPanel {
  private val component =
    ScalaComponent.builder[(AppState, AppBackend, AppProps)]("MainPanel")
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


          div(`class` := "main-panel")(
            TopBar(state, backend),
            EditorTopBar(state, backend, props.snippetId),
            div(`id` := "content")(
              div(`id` := "editor-container",
                  `class` := "inner-container",
                  show(View.Editor)
              )(
                div(`id` := "code")(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(`id` := "settings-container",
                  `class` := "inner-container",
                  show(View.BuildSettings)
              )(
                BuildSettings(state, backend)
              ),
              div(`id` := "snippets-container",
                  `class` := "inner-container",
                  show(View.CodeSnippets))(
                CodeSnippets(props.router, state, backend)
              ),
              MobileBar(state, backend)
            )
          )
        }
      }
      .build

  def apply(state: AppState, backend: AppBackend, props: AppProps) =
    component((state, backend, props))
}
