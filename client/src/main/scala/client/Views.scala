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
            TagMod(
              div(`class` := "sk-folding-cube",
                  onClick ==> setView(View.Editor))(
                div(`class` := "sk-cube1 sk-cube"),
                div(`class` := "sk-cube2 sk-cube"),
                div(`class` := "sk-cube4 sk-cube"),
                div(`class` := "sk-cube3 sk-cube")
              ),
              p("Running")
            )
          } else {
            if (View.Editor == state.view) {
              // RUN
              TagMod(
                mediaPlay(
                  onClick ==> run,
                  `class` := "runnable"
                ),
                p("Run")
              )
            } else {
              TagMod(
                mediaPlay(
                  onClick ==> setView(View.Editor),
                  title := "Edit code"
                ),
                p("Edit")
              )
            }
          }

        val consoleSelected =
          if (state.console) TagMod(`class` := "toggle selected")
          else EmptyTag

        val instrumentationSelected =
          if (state.inputs.isInstrumented) TagMod(`class` := "toggle selected")
          else EmptyTag

        val sharing =
          if(!state.saved) {
            li(
              iconic.pencil(onClick ==> save),
              p("Save")
            )
          } else {
            TagMod(
              li(
                iconic.pencil(onClick ==> update),
                p("Update")
              ),
              li(
                iconic.fork(onClick ==> fork),
                p("Fork")
              )
            )
          }

        val autoformatSelected = 
          if(state.inputs.autoformat) TagMod(`class` := "toggle selected")
          else EmptyTag

        nav(`class` := s"sidebar $theme")(
          ul(
            li(selected(View.Editor))(
              editor
            ),
            sharing,
            li(selected(View.Settings))(
              cog(onClick ==> setView(View.Settings)),
              p("Settings")
            ),
            li(consoleSelected)(
              terminal(onClick ==> toggleConsole),
              p("Console")
            ),
            li(autoformatSelected)(
              iconic.justifyLeft(onClick ==> toggleAutoformat),
              p("Format")
            ),
            li(instrumentationSelected)(
              iconic.script(onClick ==> toggleInstrumentation),
              p("Script")
            )
          )
        )
    }.build

  def apply(state: State, backend: Backend) = component((state, backend))
}
