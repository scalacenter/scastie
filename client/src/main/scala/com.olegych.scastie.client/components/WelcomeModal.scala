package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object WelcomeModal {

  def apply(isClosed: Boolean, close: Callback) = component((isClosed, close))

  private val component =
    ScalaComponent
      .builder[(Boolean, Callback)]("WelcomeModal")
      .render_P {
        case (isClosed, close) =>
          Modal(
            "Welcome to Scastie!",
            isClosed,
            close,
            TagMod(),
            TagMod(
              p(clazz := "modal-intro",
                "Scastie is an interactive playground for Scala."),
              h2("Run / Edit"),
              p(clazz := "modal-intro",
                "The code editor where you can write and run your code."),
              h2("Build Settings"),
              p(
                clazz := "modal-intro",
                """In Build Settings you can change the Scala version and add libraries,
                          choose your desired target and even add your own custom sbt configuration."""
              ),
              h2("User's Code Snippets"),
              p(
                clazz := "modal-intro",
                "Your saved code fragments will appear here and you’ll be able to edit or share them."
              ),
              h2("Console"),
              p(
                clazz := "modal-intro",
                "You can see your code’s output in the Scastie’s console."
              ),
              h2("Feedback"),
              p(clazz := "modal-intro",
                "You can join our Gitter channel and send issues.")
            )
          )
      }
      .build
}
