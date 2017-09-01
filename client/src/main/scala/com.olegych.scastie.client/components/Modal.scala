package com.olegych.scastie.client.components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class Modal(title: String,
                       isClosed: Boolean,
                       close: Reusable[Callback],
                       modalcls: TagMod,
                       content: TagMod) {
  @inline def render: VdomElement = Modal.component(this)
}

object Modal {

  implicit val reuse: Reusability[Modal] =
    Reusability.byRefOr_==

  private def render(props: Modal): VdomElement = {
    val modalStyle =
      if (props.isClosed) TagMod(display.none)
      else TagMod(display.block)

    div(cls := "modal", modalStyle)(
      div(cls := "modal-fade-screen",
          onClick ==> (e => e.stopPropagationCB >> props.close))(
        div(cls := "modal-window",
            props.modalcls,
            onClick ==> (e => e.stopPropagationCB))(
          div(cls := "modal-header")(
            div(cls := "modal-close",
                onClick ==> (e => e.stopPropagationCB >> props.close),
                role := "button",
                title := "close help modal")
          )(
            h1(props.title)
          ),
          div(cls := "modal-inner")(
            props.content
          )
        )
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[Modal]("Modal")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdateWithOverlay)
      .build
}
