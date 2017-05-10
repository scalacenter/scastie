package com.olegych.scastie
package client
package components

import api.SnippetId

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object EditorTopBar {

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend, Option[SnippetId])]("EditorTopBar")
      .render_P {
        case (state, backend, snippetId) =>
          def isDisabled =
            if (state.view != View.Editor) TagMod(clazz := "disabled")
            else EmptyVdom

          nav(clazz := "editor-topbar", isDisabled)(
            ul(clazz := "editor-buttons")(
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
