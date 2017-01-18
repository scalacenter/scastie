package client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

import org.scalajs.dom.raw.HTMLPreElement

sealed trait View
object View {
  case object Editor extends View
  case object Settings extends View
}

object MainPannel {

  val console = Ref[HTMLPreElement]("console")

  private val component =
    ReactComponentB[(State, Backend)]("MainPannel").render_P {
      case (state, backend) =>
        def show(view: View): TagMod =
          if (view == state.view) TagMod(display.block)
          else TagMod(display.none)

        val theme = if (state.dark) "dark" else "light"

        val consoleCss =
          if (state.console) "with-console"
          else ""

        div(`class` := "main-pannel")(
          div(`class` := s"pannel $consoleCss $theme", show(View.Editor))(
            Editor(state, backend),
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

  def apply(state: State, backend: Backend) = component((state, backend))
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

        val theme = if (state.dark) "dark" else "light"

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        val editor =
          if (state.running) {
            div(`class` := "sk-folding-cube",
                title := "Running...",
                onClick ==> setView(View.Editor))(
              div(`class` := "sk-cube1 sk-cube"),
              div(`class` := "sk-cube2 sk-cube"),
              div(`class` := "sk-cube4 sk-cube"),
              div(`class` := "sk-cube3 sk-cube")
            )
          } else {
            if (View.Editor == state.view) {
              // RUN
              mediaPlay(onClick ==> run2,
                        `class` := "runnable",
                        title := "Run")
            } else {
              mediaPlay(onClick ==> setView(View.Editor), title := "Edit code")
            }
          }

        val consoleSelected =
          if (state.console) TagMod(`class` := "toggle selected")
          else EmptyTag

        val instrumentationSelected =
          if (state.inputs.isInstrumented) TagMod(`class` := "toggle selected")
          else EmptyTag

        nav(`class` := s"sidebar $theme")(
          ul(
            li(selected(View.Editor))(editor),
            li(selected(View.Settings))(
              cog(onClick ==> setView(View.Settings))
            ),
            li(consoleSelected, title := "Toggle console")(
              terminal(onClick ==> toggleConsole)
            ),
            li(instrumentationSelected, title := "Toggle worksheet")(
              iconic.script(onClick ==> toggleInstrumentation)
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) = component((state, backend))
}
