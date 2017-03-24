package com.olegych.scastie
package client

import com.olegych.scastie.client.DefaultSizes._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.raw.HTMLPreElement
import org.scalajs.dom.window._

object MainPanel {

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val component =
    ReactComponentB[(AppState, AppBackend, AppProps)]("MainPanel").render_P {
      case (state, backend, props) =>
        def show(view: View) = {
          if (view == state.view) TagMod(display.block)
          else TagMod(display.none)
        }

        val embedded = props.embedded.isDefined

        val embeddedMenu =
          if (embedded) TagMod(EmbeddedMenu(state, backend))
          else EmptyTag

        def editorStyle: TagMod = Seq(
          height := innerHeight - topBarHeight - (if(state.consoleIsOpen) innerHeight*consoleHeight else consoleBarHeight),
          width := innerWidth - sideBarWidth)

        def containerStyle: TagMod = Seq(
          height := innerHeight - topBarHeight,
          width := innerWidth - sideBarWidth)

        div(`class` := "main-panel")(
          TopBar(state, backend),
          div(`id` := "content")(
            div(`id`:= "editor-container", `class` := "inner-container", editorStyle, show(View.Editor))(
              div(`id`:= "code", editorStyle)(Editor(state, backend), embeddedMenu),
              Console(state, backend)),
            div(`id`:= "settings-container", `class` := "inner-container", containerStyle, show(View.BuildSettings))(
              BuildSettings(state, backend)),
            div(`id`:= "snippets-container", `class` := "inner-container", containerStyle, show(View.CodeSnippets))(
              CodeSnippets(props.router, state))
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
