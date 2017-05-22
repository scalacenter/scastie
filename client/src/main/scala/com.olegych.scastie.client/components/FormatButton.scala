package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class FormatButton(inputsHasChanged: Boolean, formatCode: Callback) {
  @inline def render: VdomElement = FormatButton.component(this)
}

object FormatButton {

  private def render(props: FormatButton): VdomElement = {
    li(title := "Format Code (F6)",
       role := "button",
       (cls := "disabled").when(!props.inputsHasChanged),
       cls := "btn",
       onClick --> props.formatCode)(
      i(cls := "fa fa-align-left"),
      span("Format")
    )
  }

  private val component =
    ScalaComponent
      .builder[FormatButton]("FormatButton")
      .render_P(render)
      .build
}
