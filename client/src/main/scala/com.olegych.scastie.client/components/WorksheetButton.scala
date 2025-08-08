package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._

import vdom.all._

import com.olegych.scastie.client.i18n.I18n

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

    val isWorksheetModeToggleLabel =
      if (props.isWorksheetMode) "OFF"
      else "ON"

    li(
      title := (if (props.hasWorksheetMode)
                  I18n.t(s"Turn Worksheet mode $isWorksheetModeToggleLabel (evaluate and print each top level expression)")
                else I18n.t("This configuration does not support Worksheet mode")),
      isWorksheetModeSelected,
      role := "button",
      cls := "btn editor",
      onClick --> props.toggleWorksheetMode
    )(
      i(cls := "fa fa-calendar"),
      span(I18n.t("Worksheet")),
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
