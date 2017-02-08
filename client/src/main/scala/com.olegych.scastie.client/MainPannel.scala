package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.HTMLPreElement

object MainPannel {

  val console = Ref[HTMLPreElement]("console")

  private val component =
    ReactComponentB[(State, Backend, Boolean)]("MainPannel").render_P {
      case (state, backend, embedded) =>
        def show(view: View) = {
          val showTag = TagMod(display.block)
          if(embedded && view == View.Editor) showTag
          if (view == state.view) showTag
          else TagMod(display.none)
        }
          
        val theme = if (state.isDarkTheme) "dark" else "light"

        val consoleCss =
          if (state.consoleIsOpen) "with-console"
          else ""

        val embeddedMenu = 
          if (embedded) TagMod(EmbeddedMenu(state, backend))
          else EmptyTag

        div(`class` := "main-pannel")(
          div(`class` := s"pannel $theme $consoleCss", show(View.Editor))(
            Editor(state, backend),
            embeddedMenu,
            pre(`class` := "output-console", ref := console)(
              state.outputs.console.mkString("")
            )
          ),
          div(`class` := s"pannel $theme", show(View.Settings))(
            Settings(state, backend))
        )
    }.componentDidUpdate(scope =>
      Callback {
        val consoleDom = console(scope.$).get
        consoleDom.scrollTop = consoleDom.scrollHeight.toDouble
      }
    )
    .build

  def apply(state: State, backend: Backend, embedded: Boolean) = component((state, backend, embedded))
}
