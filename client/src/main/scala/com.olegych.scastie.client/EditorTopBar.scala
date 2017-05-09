package com.olegych.scastie
package client

import api.SnippetId

import japgolly.scalajs.react._, vdom.all._

object EditorTopBar {

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend, Option[SnippetId])]("EditorTopBar")
      .render_P {
        case (state, backend, snippetId) =>
          def isDisabled =
            if (state.view != View.Editor) TagMod(`class` := "disabled")
            else EmptyVdom

          nav(`class` := "editor-topbar", isDisabled)(
            ul(`class` := "editor-buttons")(
              RunButton(state, backend),
              NewButton(state, backend),
              FormatButton(state, backend),
              ClearButton(state, backend),
              WorksheetButton(state, backend),
              SaveButton(state, backend, snippetId)
            )
          )
      }
      .build

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
