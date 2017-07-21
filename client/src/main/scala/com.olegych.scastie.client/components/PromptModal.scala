package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class PromptModal(modalText: String,
                             isClosed: Boolean,
                             close: Callback,
                             actionText: String,
                             actionLabel: String,
                             action: Callback) {

  @inline def render: VdomElement = PromptModal.component(this)
}

object PromptModal {
  private def render(props: PromptModal): VdomElement = {
    Modal(
      props.modalText,
      props.isClosed,
      props.close,
      TagMod(cls := "modal-reset"),
      TagMod(
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
          li(onClick ==> (e => e.stopPropagationCB >> props.close),
             cls := "btn")(
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
      .build
}
