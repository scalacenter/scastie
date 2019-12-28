package com.olegych.scastie.client.components

import com.olegych.scastie.client.components.editor.EditorOptions
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.vdom.all._

final case class FormatButton(inputsHasChanged: Boolean, isStatusOk: Boolean, formatCode: Reusable[Callback]) {
  @inline def render: VdomElement = FormatButton.component(this)
}

object FormatButton {
  implicit val reusability: Reusability[FormatButton] = Reusability.derive[FormatButton]

  private def render(props: FormatButton): VdomElement = {
    li(
      title := s"Format Code (${EditorOptions.Keys.format})",
      role := "button",
      cls := "btn",
      onClick --> props.formatCode,
    )(
      i(cls := "fa fa-align-left"),
      span("Format")
    )
  }

  private val component =
    ScalaComponent
      .builder[FormatButton]("FormatButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
