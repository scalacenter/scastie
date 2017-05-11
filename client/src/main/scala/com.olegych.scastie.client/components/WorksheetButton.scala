package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object WorksheetButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("WorksheetButton")
      .render_P {
        case (state, backend) =>
          val worksheetModeSelected =
            if (state.inputs.worksheetMode && state.view != View.Editor)
              TagMod(clazz := "enabled alpha")
            else if (state.inputs.worksheetMode) TagMod(clazz := "enabled")
            else EmptyVdom

          val worksheetModeToogleLabel =
            if (state.inputs.worksheetMode) "OFF"
            else "ON"

          li(title := s"Turn Worksheet Mode $worksheetModeToogleLabel (F4)",
             worksheetModeSelected,
             role := "button",
             clazz := "btn editor",
             onClick --> backend.toggleWorksheetMode)(
            i(clazz := "fa fa-calendar"),
            span("Worksheet"),
            i(clazz := "workSheetIndicator",
              clazz := "fa fa-circle",
              worksheetModeSelected)
          )
      }
      .build
}
