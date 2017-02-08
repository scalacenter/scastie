package com.olegych.scastie
package client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

import org.scalajs.dom.raw.HTMLPreElement

sealed trait View
object View {
  case object Editor extends View
  case object Settings extends View


  import upickle.default._

  implicit val pkl: ReadWriter[View] =
    macroRW[Editor.type] merge macroRW[Settings.type]
}

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

        val embeddedRunButton = if (embedded) TagMod(EmbeddedRunButton(state, backend)) else EmptyTag

        div(`class` := "main-pannel")(
          div(`class` := s"pannel $consoleCss $theme", show(View.Editor))(
            Editor(state, backend),
            embeddedRunButton,
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
      })
      .build

  def apply(state: State, backend: Backend, embedded: Boolean) = component((state, backend, embedded))
}

object ConsoleOutput {
  private val component = ReactComponentB[Vector[String]]("Console Output")
    .render_P(console => ul(`class` := "output")(console.map(o => li(o))))
    .build

  def apply(console: Vector[String]) = component(console)
}


object SideBar {
  private val component =
    ReactComponentB[(State, Backend)]("SideBar").render_P {
      case (state, backend) =>
        import backend._

        val theme = if (state.isDarkTheme) "dark" else "light"

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag



        val consoleSelected =
          if (state.consoleIsOpen) TagMod(`class` := "toggle selected")
          else EmptyTag

        val instrumentationSelected =
          if (state.inputs.isInstrumented) TagMod(`class` := "toggle selected")
          else EmptyTag

        nav(`class` := s"sidebar $theme")(
          ul(
            li(`class` := "button", selected(View.Editor))(
              RunButton(state, backend)
            ),
            li(`class` := "button" )(
              iconic.pencil(onClick ==> save),
              p("Save")
            ),
            li(`class` := "button", selected(View.Settings))(
              img(src := "/assets/dotty3.svg",
                  alt := "settings",
                  `class` := "libraries",
                  onClick ==> setView(View.Settings)),
              p("Libraries")
            ),
            li(`class` := "button", consoleSelected)(
              terminal(onClick ==> toggleConsole),
              p("Console")
            ),
            li(`class` := "button")(
              iconic.justifyLeft(onClick ==> formatCode),
              p("Format")
            ),
            li(`class` := "button", instrumentationSelected)(
              iconic.script(onClick ==> toggleInstrumentation),
              p("Script")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) = component((state, backend))
}
