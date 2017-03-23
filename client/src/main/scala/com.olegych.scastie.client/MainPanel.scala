package com.olegych.scastie
package client

import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom.raw.{HTMLElement, HTMLPreElement}
import org.scalajs.dom.window._

object MainPanel {

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val topbarElement = Ref[HTMLElement]("topbar")
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

        def toogleShowHelpAtStartup(e: ReactEvent): Callback = {
          backend.toggleHelpAtStartup()
        }

        def closeHelp(e: ReactEvent): Callback = {
          backend.closeHelp()
        }

        val showHelp =
          if (state.isShowingHelpAtStartup && state.isStartup && !embedded)
            true
          else !state.isHelpModalClosed

        val helpClosepanel =
          if (showHelp) {
            TagMod(
              div(`class` := "help-close")(
                button(onClick ==> closeHelp)("Close"),
                div(`class` := "not-again")(
                  p("Dont show again"),
                  input.checkbox(onChange ==> toogleShowHelpAtStartup,
                                 checked := !state.isShowingHelpAtStartup)
                )
              )
            )
          } else EmptyTag

        val helpState =
          if (showHelp) {
            val helpModal =
              api.Instrumentation(api.Position(0, 0),
                                  api.runtime.help.copy(folded = false))

            state.copy(
              outputs = state.outputs.copy(
                instrumentations = state.outputs.instrumentations + helpModal)
            )
          } else state

        val topBarHeight = 70
        val sideBarWidth = 149
        val consoleBarHeight: Double = 33
        lazy val consoleHeight = innerHeight*0.25

        def editorStyle: TagMod = Seq(
          height := innerHeight - topBarHeight - (if(state.consoleIsOpen) consoleHeight else consoleBarHeight),
          width := innerWidth - sideBarWidth)

        def containerStyle: TagMod = Seq(
          height := innerHeight - topBarHeight,
          width := innerWidth - sideBarWidth)

        div(`class` := "main-panel")(
          TopBar(state, backend),
          div(`id` := "content")(
            div(`id`:= "editor-container", `class` := "inner-container", editorStyle, show(View.Editor))(
              div(`id`:= "code", editorStyle)(Editor(helpState, backend), embeddedMenu),
              Console(state, backend)),
            div(`id`:= "settings-container", `class` := "inner-container", containerStyle, show(View.BuildSettings))(
              BuildSettings(state, backend)),
            div(`id`:= "snippets-container", `class` := "inner-container", containerStyle, show(View.CodeSnippets))(
              CodeSnippets(props.router, state.view))
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
