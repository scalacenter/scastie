package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom

object EditorTopBar {

  private val component =
    ScalaComponent.builder[(AppState, AppBackend, Option[SnippetId])]("EditorTopBar").render_P {
      case (state, backend, snippetId) =>
        def isDisabled =
          if (state.view != View.Editor) TagMod(`class` := "disabled")
          else EmptyVdom

        def topBarStyle = Seq(
          minWidth :=
            (if (state.dimensions.forcedDesktop)
               Dimensions.default.minWindowWidth
             else
               dom.window.innerWidth.toInt - state.dimensions.sideBarWidth).px
        )

        nav(`id` := "editor-topbar", isDisabled, topBarStyle.toTagMod)(
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
