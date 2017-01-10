package client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

sealed trait View
object View {
  case object Editor   extends View
  case object Settings extends View
  case object Output   extends View
}

object MainPannel {
  private val component =
    ReactComponentB[(State, Backend)]("MainPannel").render_P {
      case (state, backend) =>
        def show(view: View): TagMod =
          if (view == state.view) TagMod(display.block)
          else TagMod(display.none)

        val theme = if (state.dark) "dark" else "light"

        div(`class` := "main-pannel")(
          div(`class` := "pannel", show(View.Editor))(Editor(state, backend)),
          div(`class` := s"pannel $theme", show(View.Settings))(
            Settings(state, backend)),
          div(`class` := s"pannel $theme", show(View.Output))(
            ConsoleOutput(state.outputs.console))
        )
    }.build

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
                onClick ==> setView(View.Editor))(
              div(`class` := "sk-cube1 sk-cube"),
              div(`class` := "sk-cube2 sk-cube"),
              div(`class` := "sk-cube4 sk-cube"),
              div(`class` := "sk-cube3 sk-cube")
            )
          } else {
            if (View.Editor == state.view) {
              // RUN
              mediaPlay(onClick ==> run2, `class` := "runnable")
            } else {
              mediaPlay(onClick ==> setView(View.Editor))
            }
          }

        nav(`class` := s"sidebar $theme")(
          ul(
            li(selected(View.Editor))(editor),
            li(selected(View.Output))(
              terminal(onClick ==> setView(View.Output))),
            li(selected(View.Settings))(
              cog(onClick ==> setView(View.Settings)))
          )
        )
    }.build

  def apply(state: State, backend: Backend) = component((state, backend))
}
