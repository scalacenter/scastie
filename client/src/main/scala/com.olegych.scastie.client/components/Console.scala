package com.olegych.scastie.client.components

import com.olegych.scastie.api.ConsoleOutput
import com.olegych.scastie.client.{ConsoleState, HTMLFormatter}
import org.scalajs.dom.raw.HTMLDivElement
import japgolly.scalajs.react._
import vdom.all._
import extra._

final case class Console(isOpen: Boolean,
                         isRunning: Boolean,
                         consoleOutputs: Vector[ConsoleOutput],
                         close: Reusable[Callback],
                         open: Reusable[Callback]) {
  @inline def render: VdomElement = Console.component(this)
}

object Console {

  implicit val reusability: Reusability[Console] =
    Reusability.caseClass[Console]

  private var consoleElement: HTMLDivElement = _

  def render(props: Console): VdomElement = {
    val (displayConsole, displaySwitcher) =
      if (props.isOpen) (display.block, display.none)
      else (display.none, display.block)

    val consoleCss =
      if (props.isOpen)
        TagMod(cls := "console-open")
      else EmptyVdom

    val (users, systems) = props.consoleOutputs.partition {
      case u: ConsoleOutput.UserOutput => true
      case _                           => false
    }

    val toShow =
      if (users.isEmpty) props.consoleOutputs
      else users

    def renderConsoleOutputs: String = {
      val out =
        toShow
          .map(
            output => s"<li>${HTMLFormatter.format(output.show)}</li>"
          )
          .mkString("\n  ")

      s"""|<ol>
          |$out
          |</ol>""".stripMargin
    }

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
        div.ref(consoleElement = _)(
          cls := "output-console",
          dangerouslySetInnerHtml := renderConsoleOutputs
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
      .configure(Reusability.shouldComponentUpdate)
      .build
}
