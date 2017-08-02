package com.olegych.scastie.client.components

import org.scalajs.dom.raw.HTMLPreElement

import japgolly.scalajs.react._, vdom.all._

final case class Console(isOpen: Boolean,
                         close: Callback,
                         open: Callback,
                         content: String) {
  @inline def render: VdomElement = Console.component(this)
}

object Console {

  private var consoleElement: HTMLPreElement = _

  def render(props: Console): VdomElement = {
    val (displayConsole, displaySwitcher) =
      if (props.isOpen) (display.block, display.none)
      else (display.none, display.block)

    val consoleCss =
      if (props.isOpen)
        TagMod(cls := "console-open")
      else EmptyVdom

    div(cls := "console-container", consoleCss)(
      div(cls := "console", displayConsole)(
        div(cls := "handler"),
        div(cls := "switcher-hide",
            displayConsole,
            role := "button",
            onClick --> props.close)(
          i(cls := "fa fa-terminal"),
          "Console",
          i(cls := "fa fa-caret-down")
        ),
        pre.ref(consoleElement = _)(cls := "output-console")(
          props.content
        )
      ),
      div(cls := "switcher-show", role := "button", onClick --> props.open)(
        displaySwitcher,
        i(cls := "fa fa-terminal"),
        "Console",
        i(cls := "fa fa-caret-up")
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[Console]("Console")
      .initialState(ConsoleStateHelper.default)
      .render_P(render)
      .componentDidUpdate(
        scope =>
          Callback {
            consoleElement.scrollTop = consoleElement.scrollHeight.toDouble
        }
      )
      .build
}
