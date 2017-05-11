package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object EditorButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("EditorButton")
      .render_P {
        case (state, backend) =>
          def selected(view: View) =
            if (view == state.view) TagMod(clazz := "selected")
            else EmptyVdom

          li(onClick --> backend.setView(View.Editor),
             title := "Open Editor View",
             role := "button",
             clazz := "btn run-button",
             selected(View.Editor))(
            i(clazz := "fa fa-edit"),
            span("Editor")
          )

      }
      .build
}
