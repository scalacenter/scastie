package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._

import vdom.all._

final case class PromptModal(
                             isDarkTheme: Boolean,
                             modalText: String,
                             modalId: String,
                             isClosed: Boolean,
                             close: Reusable[Callback],
                             actionText: String,
                             actionLabel: String,
                             action: Reusable[Callback]) {

  @inline def render: VdomElement = PromptModal.component(this)
}

object PromptModal {

  implicit val reusability: Reusability[PromptModal] =
    Reusability.derive[PromptModal]

  private def render(props: PromptModal): VdomElement = {
    Modal(
      title = props.modalText,
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(cls := "modal-reset"),
      modalId = props.modalId,
      content = TagMod(
        p(
          cls := "modal-intro",
          props.actionText
        ),
        ul(
          li(onClick ==> (
                 e => e.stopPropagationCB >> props.action >> props.close
             ),
             cls := "btn")(
            props.actionLabel
          ),
          li(onClick ==> (e => e.stopPropagationCB >> props.close), cls := "btn")(
            "Cancel"
          )
        )
      )
    ).render
  }

  private val component =
    ScalaComponent
      .builder[PromptModal]("PrompModal")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
