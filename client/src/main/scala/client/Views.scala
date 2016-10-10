package client

import App._

import japgolly.scalajs.react._, vdom.all._

import iconic._

sealed trait View
object View {
  case object Editor extends View
  case object Output extends View
  case object Settings extends View
}

object MainPannel {
  private val component = ReactComponentB[(State, Backend)]("MainPannel")
    .render_P { case (state, backend) =>
      // import backend._

      div(`class` := "main-pannel")(
        Editor(state, backend),
        Settings(state, backend),
        ConsoleOutput(state.outputs.console)
      )
    }
    .build


  def apply(state: State, backend: Backend) = component((state, backend))
}

object Settings {
  private val component = ReactComponentB[(State, Backend)]("Settings")
    .render_P { case (state, backend) =>
      // import backend._

      div("settings")
    }
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
  private val component = ReactComponentB[(State, Backend)]("SideBar")
    .render_P { case (state, backend) =>
      import backend._

      val theme = if(state.dark) "dark" else "light"

      nav(`class` := s"sidebar $theme")(
        ul(
          li(`class` := "selected")(mediaPlay(onClick ==> setView(View.Editor))), // clock()
          li(cog(onClick ==> setView(View.Settings))),
          li(terminal(onClick ==> setView(View.Output)))
        )
      )
    }
    .build

  def apply(state: State, backend: Backend) = component((state, backend))
}
