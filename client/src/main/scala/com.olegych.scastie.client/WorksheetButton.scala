package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object WorksheetButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("WorksheetButton").render_P {
      case (state, backend) =>
        def disabled(isDisabled: Boolean) =
          if (isDisabled) "disabled"
          else ""

        val worksheetModeSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "toggle selected")
          else EmptyTag

        val worksheetModeToogleLabel =
          if (!state.inputs.worksheetMode) "OFF"
          else "ON"

        val worksheetModeClassSelected =
          if (state.inputs.worksheetMode) TagMod(`class` := "toggle selected")
          else EmptyTag

        fieldset(
          legend("Options"),
          button(
            onClick ==> backend.toggleWorksheetMode,
            title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
            worksheetModeSelected,
            `class` := "btn",
            worksheetModeClassSelected)(
            iconic.script,
            p(s"Worksheet $worksheetModeToogleLabel")
          )
        )

        li(onClick ==> backend.toggleWorksheetMode,
          title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
          worksheetModeSelected,
           `class` := "btn")(
          i(`class` := "fa fa-calendar"),
          "Worksheet"
        )
    }.build
}
