package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object EditorButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("EditorButton").render_P {
      case (state, backend) =>
        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyVdom

        li(onClick ==> backend.setView2(View.Editor),
           title := "Open Editor View",
           role := "button",
           `class` := "btn run-button",
           selected(View.Editor))(
          i(`class` := "fa fa-edit"),
          span("Editor")
        )

    }.build
}
