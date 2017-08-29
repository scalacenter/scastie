package com.olegych.scastie.client.components

import com.olegych.scastie.client.ConsoleState

import org.scalajs.dom.raw.HTMLPreElement

import japgolly.scalajs.react._, vdom.all._, extra._

final case class Console(isOpen: Boolean,
                         isRunning: Boolean,
                         content: String,
                         close: Reusable[Callback],
                         open: Reusable[Callback]) {
  @inline def render: VdomElement = Console.component(this)
}

object Console {

  implicit val reusability: Reusability[Console] =
    Reusability.caseClass[Console]

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
      .initialState(ConsoleState.default)
      .render_P(render)
      .componentDidUpdate(
        scope =>
          Callback {
            consoleElement.scrollTop = consoleElement.scrollHeight.toDouble
          }.when_(scope.prevProps.isRunning)
      )
      .configure(Reusability.shouldComponentUpdateWithOverlay)
      .build
}
