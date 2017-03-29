package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.raw.HTMLPreElement
import org.scalajs.dom.window._


object MainPanel {

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val component =
    ReactComponentB[(AppState, AppBackend, AppProps)]("MainPanel").render_P {
      case (state, backend, props) =>

        import state.dimensions._

        def show(view: View) = {
          if (view == state.view) TagMod(display.block)
          else TagMod(display.none)
        }

        val embedded = props.embedded.isDefined

        val embeddedMenu =
          if (embedded) TagMod(EmbeddedMenu(state, backend))
          else EmptyTag

        def editorStyle: TagMod = Seq(
          height := s"${innerHeight - topBarHeight - editorTopBarHeight -
            (if(state.consoleState.consoleIsOpen) consoleHeight else consoleBarHeight)}px",
          width := s"${innerWidth - sideBarWidth}px")

        def containerStyle: TagMod = Seq(
          height := s"${innerHeight - topBarHeight}px",
          width := s"${innerWidth - sideBarWidth}px")

        div(`class` := "main-panel")(
          TopBar(state, backend),
          EditorTopBar(state, backend, props.snippetId),
          div(`id` := "content")(
            div(`id`:= "editor-container", `class` := "inner-container", editorStyle, show(View.Editor))(
              div(`id`:= "code", editorStyle)(Editor(state, backend), embeddedMenu),
              Console(state, backend)),
            div(`id`:= "settings-container", `class` := "inner-container", containerStyle, show(View.BuildSettings))(
              BuildSettings(state, backend)),
            div(`id`:= "snippets-container", `class` := "inner-container", containerStyle, show(View.CodeSnippets))(
              CodeSnippets(props.router, state, backend)),
            MobileBar(state, backend)
          )
        )

    }.componentDidUpdate(scope =>
      Callback {
        consoleElement(scope.$).foreach{consoleDom =>
          consoleDom.scrollTop = consoleDom.scrollHeight.toDouble
        }
      }
    ).build

  def apply(state: AppState, backend: AppBackend, props: AppProps) =
    component((state, backend, props))
}
