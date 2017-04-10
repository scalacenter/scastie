package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom

object EditorTopBar {

  private val component =
    ReactComponentB[(AppState, AppBackend, Option[SnippetId])]("EditorTopBar").render_P {
      case (state, backend, snippetId) =>
        def isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled")
          else EmptyTag

        def topBarStyle: TagMod = Seq(
          minWidth := s"${
            if(state.dimensions.forcedDesktop) Dimensions.default.minWindowWidth
            else dom.window.innerWidth - state.dimensions.sideBarWidth}px"
        )

        nav(`id` := "editor-topbar", isDisabled, topBarStyle)(
          ul(`class` := "editor-buttons")(
            RunButton(state, backend),
            NewButton(state, backend),
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
