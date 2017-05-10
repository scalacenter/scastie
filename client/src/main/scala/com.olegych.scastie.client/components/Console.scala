package com.olegych.scastie
package client
package components

import org.scalajs.dom.raw.HTMLPreElement

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object Console {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private var consoleElement: HTMLPreElement = _

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("Console")
      .initialState(ConsoleState.default)
      .render_P {
        case (state, backend) =>
          val (displayConsole, displaySwitcher) =
            if (state.consoleState.consoleIsOpen) (display.block, display.none)
            else (display.none, display.block)

          val consoleCss =
            if (state.consoleState.consoleIsOpen)
              TagMod(clazz := "console-open")
            else EmptyVdom

          div(clazz := "console-container", consoleCss)(
            div(clazz := "console", displayConsole)(
              div(clazz := "handler"),
              div(clazz := "switcher-hide",
                  displayConsole,
                  role := "button",
                  onClick ==> backend.toggleConsole)(
                i(clazz := "fa fa-terminal"),
                "Console",
                i(clazz := "fa fa-caret-down")
              ),
              pre.ref(consoleElement = _)(clazz := "output-console")(
                state.outputs.console
              )
            ),
            div(clazz := "switcher-show",
                role := "button",
                onClick ==> backend.toggleConsole)(
              displaySwitcher,
              i(clazz := "fa fa-terminal"),
              "Console",
              i(clazz := "fa fa-caret-up")
            )
          )

      }
      .componentDidUpdate(
        scope =>
          Callback {
            consoleElement.scrollTop = consoleElement.scrollHeight.toDouble
        }
      )
      .build
}
