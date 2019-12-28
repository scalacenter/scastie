package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class WelcomeModal(isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = WelcomeModal.component(this)
}

object WelcomeModal {

  implicit val reusability: Reusability[WelcomeModal] =
    Reusability.derive[WelcomeModal]

  private def render(props: WelcomeModal): VdomElement = {
    Modal(
      title = "Welcome to Scastie!",
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(),
      modalId = "welcome-modal",
      content = TagMod(
        p(cls := "modal-intro", "Scastie is an interactive playground for Scala."),
        h2("Run / Edit"),
        p(cls := "modal-intro", "The code editor where you can write and run your code."),
        h2("Build Settings"),
        p(
          cls := "modal-intro",
          """In Build Settings you can change the Scala version and add libraries,
                    choose your desired target and even add your own custom sbt configuration."""
        ),
        h2("User's Code Snippets"),
        p(
          cls := "modal-intro",
          "Your saved code fragments will appear here and you'll be able to edit or share them."
        ),
        h2("Console"),
        p(
          cls := "modal-intro",
          "You can see your code's output in the Scastie's console."
        ),
        h2("Feedback"),
        p(cls := "modal-intro", "You can join our Gitter channel and send issues.")
      )
    ).render
  }

  private val component =
    ScalaComponent
      .builder[WelcomeModal]("WelcomeModal")
      .render_P(render)
      .build
}
