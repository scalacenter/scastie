package org.scastie.client
package components

import org.scastie.client.components.editor.EditorKeymaps
import org.scastie.client.i18n.I18n

import japgolly.scalajs.react._

import vdom.all._

final case class ClearButton(clear: Reusable[Callback], language: String) {
  @inline def render: VdomElement = ClearButton.component(this)
}

object ClearButton {

  implicit val reusability: Reusability[ClearButton] = Reusability.derive[ClearButton]

  private def render(props: ClearButton): VdomElement = {
    li(
      title := s"${I18n.t("editor.clear_messages")} (${EditorKeymaps.clear.getName})",
      role := "button",
      cls := "btn",
      onClick --> props.clear
    )(
      div(
        i(cls := "fa fa-eraser"),
        span(I18n.t("editor.clear_messages"))
      )
    )
  }

  private val component = ScalaComponent
    .builder[ClearButton]("ClearButton")
    .render_P(render)
    .configure(Reusability.shouldComponentUpdate)
    .build

}
