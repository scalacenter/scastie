package com.olegych.scastie.client.components

import com.olegych.scastie.api.ConsoleOutput
import com.olegych.scastie.client.ConsoleState
import com.olegych.scastie.client.HTMLFormatter
import com.olegych.scastie.client.View
import japgolly.scalajs.react._
import org.scalajs.dom.raw.HTMLDivElement

import vdom.all._

final case class Console(isOpen: Boolean,
                         isRunning: Boolean,
                         isEmbedded: Boolean,
                         consoleOutputs: Vector[ConsoleOutput],
                         run: Reusable[Callback],
                         setView: View ~=> Callback,
                         close: Reusable[Callback],
                         open: Reusable[Callback]) {
  @inline def render: VdomElement = Console.component(this)
}

object Console {

  implicit val reusability: Reusability[Console] =
    Reusability.derive[Console]

  private val consoleElement = Ref[HTMLDivElement]

  def render(props: Console): VdomElement = {
    val (displayConsole, displaySwitcher) =
      if (props.isOpen) (display.block, display.none)
      else (display.none, display.flex)

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
      s"<pre>${HTMLFormatter.format(toShow.map(_.show).mkString("\n"))}</pre>"
    }

    div(cls := "console-container", consoleCss)(
      div(cls := "console", displayConsole)(
        div(cls := "handler"),
        div(cls := "switcher-hide", display.flex, role := "button", onClick --> props.close)(
          RunButton(
            isRunning = props.isRunning,
            isStatusOk = true,
            save = props.run,
            setView = props.setView,
            embedded = true,
          ).render.when(props.isEmbedded),
          div(cls := "console-label")(
            i(cls := "fa fa-terminal"),
            p("Console (F3)"),
            i(cls := "fa fa-caret-down")
          )
        ),
        div.withRef(consoleElement)(
          cls := "output-console",
          dangerouslySetInnerHtml := renderConsoleOutputs
        )
      ),
      div(cls := "switcher-show", role := "button", onClick --> props.open)(
        RunButton(
          isRunning = props.isRunning,
          isStatusOk = true,
          save = props.run,
          setView = props.setView,
          embedded = true,
        ).render.when(props.isEmbedded),
        displaySwitcher,
        div(cls := "console-label")(
          i(cls := "fa fa-terminal"),
          p("Console (F3)"),
          i(cls := "fa fa-caret-up")
        )
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
            consoleElement.unsafeGet().scrollTop = consoleElement.unsafeGet().scrollHeight.toDouble
          }.when_(scope.prevProps.isRunning)
      )
      .configure(Reusability.shouldComponentUpdate)
      .build
}
