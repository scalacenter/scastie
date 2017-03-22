package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object WorksheetButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("WorksheetButton").render_P {
      case (state, backend) =>

        val worksheetModeSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "enabled")
          else EmptyTag

        val worksheetModeToogleLabel =
          if (state.inputs.worksheetMode) "OFF"
          else "ON"

        val isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled") else TagMod(onClick ==> backend.toggleWorksheetMode)

        li(
          title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
          worksheetModeSelected,
           `class` := "btn", isDisabled)(
          i(`class` := "fa fa-calendar"),
          "Worksheet",
          i(`id` := "workSheetIndicator", `class` := "fa fa-circle", worksheetModeSelected)
        )
    }.build
}
