package org.scastie
package client
package components

import japgolly.scalajs.react._

import vdom.all._

import org.scastie.client.i18n.I18n

final case class WorksheetButton(
    hasWorksheetMode: Boolean,
    isWorksheetMode: Boolean,
    toggleWorksheetMode: Reusable[Callback],
    view: View,
    language: String
) {
  @inline def render: VdomElement = WorksheetButton.component(this)
}

object WorksheetButton {

  implicit val reusability: Reusability[WorksheetButton] =
    Reusability.derive[WorksheetButton]

  private def render(props: WorksheetButton): VdomElement = {
    val isWorksheetModeSelected =
      if (props.isWorksheetMode)
        if (props.view != View.Editor)
          TagMod(cls := "enabled alpha")
        else
          TagMod(cls := "enabled")
      else
        EmptyVdom

    li(
      title := (if (props.hasWorksheetMode)
                  if (props.isWorksheetMode)
                    I18n.t("editor.worksheet_off_tooltip")
                  else
                    I18n.t("editor.worksheet_on_tooltip")
                else I18n.t("editor.worksheet_unsupported")),
      isWorksheetModeSelected,
      role := "button",
      cls := "btn editor",
      onClick --> props.toggleWorksheetMode
    )(
      i(cls := "fa fa-calendar"),
      span(I18n.t("editor.worksheet")),
      i(cls := "workSheetIndicator", cls := "fa fa-circle", isWorksheetModeSelected)
    )
  }

  private val component =
    ScalaComponent
      .builder[WorksheetButton]("WorksheetButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
