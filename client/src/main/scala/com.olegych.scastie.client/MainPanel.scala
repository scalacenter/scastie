package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.raw.HTMLPreElement
import org.scalajs.dom.window._

object MainPanel {

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val component =
    ReactComponentB[(AppState, AppBackend, AppProps)]("MainPanel")
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
            else EmptyTag

          val editorHeight =
            innerHeight -
              topBarHeight -
              editorTopBarHeight -
              (
                if (state.consoleState.consoleIsOpen) consoleHeight
                else consoleBarHeight
              ) -
              mobileBarHeight

          def codeStyle: TagMod = Seq(
            height := editorHeight.px
          )

          def containerStyle: TagMod = Seq(
            if (forcedDesktop)
              minHeight := Dimensions.default.minWindowHeight.px
            else
              height := (innerHeight - topBarHeight).px,
            minWidth :=
              ((if (forcedDesktop) Dimensions.default.minWindowWidth
                else innerWidth.toInt) - sideBarWidth).px
          )

          div(`class` := "main-panel")(
            TopBar(state, backend),
            EditorTopBar(state, backend, props.snippetId),
            div(`id` := "content")(
              div(`id` := "editor-container",
                  `class` := "inner-container",
                  show(View.Editor),
                  containerStyle)(
                div(`id` := "code", codeStyle)(
                  Editor(state, backend),
                  embeddedMenu
                ),
                Console(state, backend)
              ),
              div(`id` := "settings-container",
                  `class` := "inner-container",
                  containerStyle,
                  show(View.BuildSettings))(
                BuildSettings(state, backend)
              ),
              div(`id` := "snippets-container",
                  `class` := "inner-container",
                  containerStyle,
                  show(View.CodeSnippets))(
                CodeSnippets(props.router, state, backend)
              ),
              MobileBar(state, backend)
            )
          )
        }
      }
      .componentDidUpdate(
        scope =>
          Callback {
            consoleElement(scope.$).foreach { consoleDom =>
              consoleDom.scrollTop = consoleDom.scrollHeight.toDouble
            }
        }
      )
      .componentDidUpdate(
        scope =>
          Callback {
            consoleElement(scope.$).foreach { consoleDom =>
              consoleDom.scrollTop = consoleDom.scrollHeight.toDouble
            }
        }
      )
      .build

  def apply(state: AppState, backend: AppBackend, props: AppProps) =
    component((state, backend, props))
}
