package com.olegych.scastie.client

import com.olegych.scastie.ConsoleState
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLPreElement
import vdom.all._

object Console {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private var consoleElement: HTMLPreElement = _

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("Console")
      .initialState(ConsoleState.default)
      .render_P {
        case (state, backend) =>
          import state.dimensions._

          val (displayConsole, displaySwitcher) =
            if (state.consoleState.consoleIsOpen) (display.block, display.none)
            else (display.none, display.block)

          val currentWidth = (dom.window.innerWidth - sideBarWidth).px

          val minConsoleWidth =
            ((if (forcedDesktop) Dimensions.default.minWindowWidth
              else dom.window.innerWidth.toInt) - sideBarWidth).px

          def consoleStyle =
            Seq(displayConsole,
                width := currentWidth,
                minWidth := minConsoleWidth)

          def switcherStyle =
            Seq(displaySwitcher,
                width := currentWidth,
                minWidth := minConsoleWidth)

          div(`id` := "console-container")(
            div(`id` := "console", consoleStyle.toTagMod)(
              div(`id` := "handler"),
              div(`id` := "switcher-hide",
                  consoleStyle.toTagMod,
                  onClick ==> backend.toggleConsole)(
                i(`class` := "fa fa-terminal"),
                "Console",
                i(`class` := "fa fa-caret-down")
              ),
              pre.ref(consoleElement = _)(`class` := "output-console")(
                state.outputs.console
              )
            ),
            div(`id` := "switcher-show", onClick ==> backend.toggleConsole)(
              switcherStyle.toTagMod,
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
