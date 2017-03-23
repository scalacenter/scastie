package com.olegych.scastie.client

import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLPreElement
import vdom.all._

object Console {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val component =
    ReactComponentB[(AppState, AppBackend)]("Console").render_P {
      case (state, backend) =>

      val (displayConsole, displaySwitcher) =
        if (state.consoleIsOpen) (display.block, display.none)
        else (display.none, display.block)

      def consoleStyle: TagMod = Seq(
        height := "25%",
        width := dom.window.innerWidth - 149,
        displayConsole)

      div(`id` := "console-container")(
        div(`id` := "console", consoleStyle)(
          div(`id` := "handler"),
          div(`id` := "switcher-hide", onClick ==> backend.toggleConsole)(
            i(`class` := "fa fa-code"),
            "Console",
            i(`class` := "fa fa-caret-down")
          ),
          pre(`class` := "output-console", ref := consoleElement)(
            state.outputs.console
          )
        ),
        div(`id`:= "switcher-show", onClick ==> backend.toggleConsole)(
          displaySwitcher,
          i(`class` := "fa fa-code"),
          "Console",
          i(`class` := "fa fa-caret-up")
        )
      )

    }.build
}
