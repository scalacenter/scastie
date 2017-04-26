package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.window._

object MainPanel {

  private val component =
    ScalaComponent.builder[(AppState, AppBackend, AppProps)]("MainPanel")
      .render_P {
        case (state, backend, props) => {

          import state.dimensions._

          def show(view: View) = {
            if (view == state.view) TagMod(display.block)
            else TagMod(display.none)
          }

          val embedded = props.embedded.isDefined

          val embeddedMenu =
            if (embedded) TagMod(EmbeddedMenu(state, backend))
            else EmptyVdom

          val editorHeight =
            innerHeight -
              topBarHeight -
              editorTopBarHeight -
              (
                if (state.consoleState.consoleIsOpen) consoleHeight
                else consoleBarHeight
              ) -
              mobileBarHeight

          def codeStyle = height := editorHeight.px

          def containerStyle = {
            val heightStyle =
              if (forcedDesktop) minHeight := Dimensions.default.minWindowHeight.px
              else height := (innerHeight - topBarHeight).px
            
            val minWidthPx =
              if (forcedDesktop) Dimensions.default.minWindowWidth
              else innerWidth.toInt

            Seq(
              heightStyle,
              minWidth := (minWidthPx - sideBarWidth).px
            )
          }

          div(`class` := "main-panel")(
            TopBar(state, backend),
            EditorTopBar(state, backend, props.snippetId),
            div(`id` := "content")(
              div(`id` := "editor-container",
                  `class` := "inner-container",
                  show(View.Editor),
                  containerStyle.toTagMod)(
                div(`id` := "code", codeStyle)(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(`id` := "settings-container",
                  `class` := "inner-container",
                  containerStyle.toTagMod,
                  show(View.BuildSettings))(
                BuildSettings(state, backend)
              ),
              div(`id` := "snippets-container",
                  `class` := "inner-container",
                  containerStyle.toTagMod,
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
