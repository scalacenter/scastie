package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object WorksheetButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("WorksheetButton").render_P {
      case (state, backend) =>
        val worksheetModeSelected =
          if (state.inputs.worksheetMode && state.view != View.Editor)
            TagMod(`class` := "enabled alpha")
          else if (state.inputs.worksheetMode) TagMod(`class` := "enabled")
          else EmptyTag

        val worksheetModeToogleLabel =
          if (state.inputs.worksheetMode) "OFF"
          else "ON"

        li(title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
           worksheetModeSelected,
           `class` := "btn editor",
           onClick ==> backend.toggleWorksheetMode)(
          i(`class` := "fa fa-calendar"),
          span("Worksheet"),
          i(`id` := "workSheetIndicator",
            `class` := "fa fa-circle",
            worksheetModeSelected)
        )
    }.build
}
