package com.olegych.scastie.client.components

import com.olegych.scastie.client.components.editor.EditorKeymaps
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

import com.olegych.scastie.client.i18n.I18n

final case class FormatButton(inputsHasChanged: Boolean, isStatusOk: Boolean, formatCode: Reusable[Callback], language: String) {
  @inline def render: VdomElement = FormatButton.component(this)
}

object FormatButton {
  implicit val reusability: Reusability[FormatButton] = Reusability.derive[FormatButton]

  private def render(props: FormatButton): VdomElement = {
    li(
      title := s"${I18n.t("editor.format_tooltip")} (${EditorKeymaps.format.getName})",
      role := "button",
      cls := "btn",
      onClick --> props.formatCode,
    )(
      i(cls := "fa fa-align-left"),
      span(I18n.t("editor.format"))
    )
  }

  private val component =
    ScalaComponent
      .builder[FormatButton]("FormatButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
