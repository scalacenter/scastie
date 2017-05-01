package com.olegych.scastie.client

import org.scalajs.dom.raw.HTMLPreElement

import japgolly.scalajs.react._, vdom.all._

object Console {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private var consoleElement: HTMLPreElement = _

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("Console")
      .initialState(ConsoleState.default)
      .render_P {
        case (state, backend) =>

          val (displayConsole, displaySwitcher) =
            if (state.consoleState.consoleIsOpen) (display.block, display.none)
            else (display.none, display.block)

          div(`id` := "console-container")(
            div(`id` := "console", displayConsole)(
              div(`id` := "handler"),
              div(`id` := "switcher-hide",
                  role := "button",
                  onClick ==> backend.toggleConsole)(
                i(`class` := "fa fa-terminal"),
                "Console",
                i(`class` := "fa fa-caret-down")
              ),
              pre.ref(consoleElement = _)(`class` := "output-console")(
                state.outputs.console
              )
            ),
            div(`id` := "switcher-show", role := "button", onClick ==> backend.toggleConsole)(
              displaySwitcher,
              i(`class` := "fa fa-terminal"),
              "Console",
              i(`class` := "fa fa-caret-up")
            )
          )

      }
      .componentDidUpdate(scope =>
        Callback {
          consoleElement.scrollTop = consoleElement.scrollHeight.toDouble
        }
      )
      .build
}
