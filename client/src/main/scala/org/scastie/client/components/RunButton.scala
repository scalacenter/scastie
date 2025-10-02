package org.scastie.client
package components

import org.scastie.client.components.editor.EditorKeymaps
import org.scastie.client.i18n.I18n

import japgolly.scalajs.react._

import vdom.all._

final case class RunButton(
    isRunning: Boolean,
    isStatusOk: Boolean,
    save: Reusable[Callback],
    setView: View ~=> Callback,
    embedded: Boolean
) {
  @inline def render: VdomElement = RunButton.component(this)
}

object RunButton {

  implicit val reusability: Reusability[RunButton] = Reusability.derive[RunButton]

  def render(props: RunButton): VdomElement = {
    if (!props.isRunning) {
      val runTitle =
        if (props.isStatusOk) s"${I18n.t("editor.run")} (${EditorKeymaps.saveOrUpdate.getName})"
        else
          s"${I18n.t("editor.run")} (${EditorKeymaps.saveOrUpdate.getName}) - ${I18n.t("editor.status_unknown_warning")}"

      li(
        onClick ==> { e => e.stopPropagationCB >> props.save },
        role := "button",
        title := runTitle,
        cls := "btn run-button"
      )(
        i(cls := "fa fa-play"),
        span(I18n.t("editor.run"))
      )
    } else {
      li(onClick --> props.setView(View.Editor), title := I18n.t("editor.running_tooltip"), cls := "btn run-button")(
        i(cls := "fa fa-spinner fa-spin"),
        span(I18n.t("editor.running"))
      )
    }
  }

  private val component = ScalaComponent
    .builder[RunButton]("RunButton")
    .render_P(render)
    .configure(Reusability.shouldComponentUpdate)
    .build

}
