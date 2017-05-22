package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class PrompModal(modalText: String,
                            isClosed: Boolean,
                            close: Callback,
                            actionText: String,
                            actionLabel: String,
                            action: Callback) {

  @inline def render: VdomElement = PrompModal.component(this)
}

object PrompModal {
  private def render(props: PrompModal): VdomElement = {
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
      .builder[PrompModal]("PrompModal")
      .render_P(render)
      .build
}
