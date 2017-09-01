package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class WorksheetButton(isWorksheetMode: Boolean,
                                 toggleWorksheetMode: Reusable[Callback],
                                 view: View) {
  @inline def render: VdomElement = WorksheetButton.component(this)
}

object WorksheetButton {

  implicit val reusability: Reusability[WorksheetButton] =
    Reusability.caseClass[WorksheetButton]

  private def render(props: WorksheetButton): VdomElement = {
    val worksheetModeSelected =
      if (props.isWorksheetMode)
        if (props.view != View.Editor)
          TagMod(cls := "enabled alpha")
        else
          TagMod(cls := "enabled")
      else
        EmptyVdom

    val worksheetModeToggleLabel =
      if (props.isWorksheetMode) "OFF"
      else "ON"

    li(
      title := s"Turn Worksheet Mode $worksheetModeToggleLabel (F4)",
      worksheetModeSelected,
      role := "button",
      cls := "btn editor",
      onClick --> props.toggleWorksheetMode
    )(
      i(cls := "fa fa-calendar"),
      span("Worksheet"),
      i(cls := "workSheetIndicator",
        cls := "fa fa-circle",
        worksheetModeSelected)
    )
  }

  private val component =
    ScalaComponent
      .builder[WorksheetButton]("WorksheetButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
