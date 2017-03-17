package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object EditorTopBar {

  private val component =
    ReactComponentB[(AppState, AppBackend, Option[SnippetId])]("EditorTopBar").render_P {
      case (state, backend, snippetId) =>
        def isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled")
          else EmptyTag

        nav(`id` := "editor-topbar", isDisabled)(
          ul(`class` := "editor-buttons")(
            RunButton(state, backend),
            FormatButton(state, backend),
            ClearButton(state, backend),
            WorksheetButton(state, backend),
            SaveButton(state, backend, snippetId)
          )
        )
    }.build

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))
}
