package com.olegych.scastie.client
package components

import japgolly.scalajs.react._, vdom.all._

final case class ClearButton(clear: Callback) {
  @inline def render: VdomElement = ClearButton.component(this)
}

object ClearButton {

  private def render(props: ClearButton): VdomElement = {
    li(title := "Clear Instrumentations (Esc)",
       role := "button",
       cls := "btn",
       onClick --> props.clear)(
      div(
        i(cls := "fa fa-eraser"),
        span("Clear")
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[ClearButton]("ClearButton")
      .render_P(render)
      .build
}
