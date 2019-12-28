package com.olegych.scastie.client
package components

import com.olegych.scastie.client.components.editor.EditorOptions
import japgolly.scalajs.react._
import vdom.all._
import extra._

final case class ClearButton(clear: Reusable[Callback]) {
  @inline def render: VdomElement = ClearButton.component(this)
}

object ClearButton {

  implicit val reusability: Reusability[ClearButton] =
    Reusability.derive[ClearButton]

  private def render(props: ClearButton): VdomElement = {
    li(title := s"Clear Messages (${EditorOptions.Keys.clear})", role := "button", cls := "btn", onClick --> props.clear)(
      div(
        i(cls := "fa fa-eraser"),
        span("Clear Messages")
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[ClearButton]("ClearButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
