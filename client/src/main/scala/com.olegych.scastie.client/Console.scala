package com.olegych.scastie.client

import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLPreElement
import vdom.all._

import scala.scalajs.js

object Console {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val consoleElement = Ref[HTMLPreElement]("console")
  private val component =
    ReactComponentB[(AppState, AppBackend)]("Console").render_P {
      case (state, backend) =>

      val (displayConsole, displaySwitcher) =
        if (state.consoleIsOpen) (TagMod(display.block), TagMod(display.none))
        else (TagMod(display.none), TagMod(display.block))

      div(`id` := "console-container")(
        div(`id` := "console",
          `style` :=
            js.Dictionary(
              "height" -> s"${dom.window.innerHeight}px",
              "width" -> s"${dom.window.innerWidth}px"))(
          displayConsole,
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
