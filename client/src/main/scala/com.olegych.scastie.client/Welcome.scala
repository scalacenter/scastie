package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object Welcome {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("Welcome").render_P {
      case (state, backend) =>
        val displayWelcome =
          if (state.modalState.isWelcomeModalClosed) display.none
          else display.block

        div(`class` := "modal", displayWelcome)(
          div(`class` := "modal-fade-screen")(
            div(`class` := "modal-window")(
              div(`class` := "modal-header")(
                div(`class` := "modal-close",
                    onClick ==> backend.toggleWelcome)
              )(
                h1("Welcome to Scastie!")
              ),
              div(`class` := "modal-inner")(
                p(`class` := "modal-intro",
                  "Scastie is an interactive playground for Scala."),
                h2("Run / Edit"),
                p(`class` := "modal-intro",
                  "The code editor where you can write and run your code."),
                h2("Build Settings"),
                p(
                  `class` := "modal-intro",
                  """In Build Settings you can change the Scala version and add libraries,
                              choose your desired target and even add your own custom sbt configuration."""
                ),
                h2("User's Code Snippets"),
                p(
                  `class` := "modal-intro",
                  "Your saved code fragments will appear here and you’ll be able to edit or share them."
                ),
                h2("Console"),
                p(`class` := "modal-intro",
                  "You can see your code’s output in the Scastie’s console."),
                h2("Feedback"),
                p(`class` := "modal-intro",
                  "You can join our Gitter channel and send issues."),
                p(`class` := "modal-intro")(
                  i(`class` := "fa fa-question-circle"),
                  "If you want to learn more about how Scastie works, you can go to our ",
                  a(href := "", "Help", onClick ==> backend.toggleWelcomeHelp),
                  "."
                )
              )
            )
          )
        )

    }.build
}
