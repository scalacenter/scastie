package com.olegych.scastie.client
package components

import com.olegych.scastie.client.components.editor.EditorKeymaps
import japgolly.scalajs.react._

import com.olegych.scastie.client.i18n.I18n

import vdom.all._

final case class RunButton(isRunning: Boolean, isStatusOk: Boolean, save: Reusable[Callback], setView: View ~=> Callback, embedded: Boolean) {
  @inline def render: VdomElement = RunButton.component(this)
}

object RunButton {

  implicit val reusability: Reusability[RunButton] =
    Reusability.derive[RunButton]

  def render(props: RunButton): VdomElement = {
    if (!props.isRunning) {
      val runTitle =
        if (props.isStatusOk)
          s"${I18n.t("Run")} (${EditorKeymaps.saveOrUpdate.getName})"
        else
          s"${I18n.t("Run")} (${EditorKeymaps.saveOrUpdate.getName}) - ${I18n.t("warning: unknown status")}"

      li(onClick ==> { e => e.stopPropagationCB >> props.save }, role := "button", title := runTitle, cls := "btn run-button")(
        i(cls := "fa fa-play"),
        span(I18n.t("Run"))
      )
    } else {
      li(onClick --> props.setView(View.Editor), title := I18n.t("Running your Code..."), cls := "btn run-button")(
        i(cls := "fa fa-spinner fa-spin"),
        span(I18n.t("Running"))
      )
    }
  }

  private val component =
    ScalaComponent
      .builder[RunButton]("RunButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
