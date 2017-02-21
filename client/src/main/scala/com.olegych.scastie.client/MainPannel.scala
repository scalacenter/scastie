package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

import org.scalajs.dom.raw.HTMLPreElement

object MainPannel { //extends vdom.Extra.Attrs {

  private val console = Ref[HTMLPreElement]("console")

  private val component =
    ReactComponentB[(State, Backend, Boolean)]("MainPannel").render_P {
      case (state, backend, embedded) =>
        def show(view: View) = {
          if (view == state.view) TagMod(display.block)
          else TagMod(display.none)
        }

        val theme = if (state.isDarkTheme) "dark" else "light"

        val consoleCss =
          if (state.consoleIsOpen) "with-console"
          else ""

        val embeddedMenu =
          if (embedded) TagMod(EmbeddedMenu(state, backend))
          else EmptyTag

        def toogleShowHelpAtStartup(e: ReactEvent): Callback = {
          backend.toggleHelpAtStartup()
        }

        def closeHelp(e: ReactEvent): Callback = {
          backend.closeHelp()
        }

        val helpMenu =
          if (state.isShowingHelpAtStartup && state.helpModal && !embedded)
            TagMod(
              div(`class` := "help-modal")(
                button(onClick ==> closeHelp)("Close"),
                div(`class` := "not-again")(
                  p("Dont show again"),
                  input.checkbox(onClick ==> toogleShowHelpAtStartup)
                ),
                div(dangerouslySetInnerHtml(api.runtime.help.a))
              )
            )
          else EmptyTag

        div(`class` := "main-pannel")(
          div(`class` := s"pannel $theme $consoleCss", show(View.Editor))(
            helpMenu,
            Editor(state, backend),
            embeddedMenu,
            pre(`class` := "output-console", ref := console)(
              state.outputs.console.mkString("")
            )
          ),
          div(`class` := s"pannel $theme", show(View.Libraries))(
            Libraries(state, backend))
        )
    }.componentDidUpdate(scope =>
        Callback {
          val consoleDom = console(scope.$).get
          consoleDom.scrollTop = consoleDom.scrollHeight.toDouble
      })
      .build

  def apply(state: State, backend: Backend, embedded: Boolean) =
    component((state, backend, embedded))
}
