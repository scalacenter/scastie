package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object SaveButton {

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) = component((state, backend, snippetId))

  private val component =
    ReactComponentB[(AppState, AppBackend, Option[SnippetId])]("SaveButton").render_P {
      case (state, backend, snippetId) =>

        val disabledIfSameInputs =
          if (!state.inputsHasChanged) "disabled"
          else ""
        
        def isDisabled(tagMod: TagMod) =
          if (state.view  != View.Editor) TagMod(`class` := "disabled") else tagMod

        import View.ctrl

        snippetId match {
          case None =>
            li(
              title := s"Save ($ctrl + S)",
              `class` := "btn", isDisabled(onClick ==> backend.save))(
              i(`class` := "fa fa-download"),
              "Save"
            )
          case Some(sid) =>
            ul(
              li(
                title := s"Amend",
                `class` := "btn", isDisabled(onClick ==> backend.amend(sid)))(
                i(`class` := "fa fa-pencil-square-o"),
                "Amend"
              ),
              li(
                title := s"Save ($ctrl + S)",
                `class` := "btn", isDisabled(onClick ==> backend.update(sid)))(
                i(`class` := "fa fa-download"),
                "Update"
              ),
              li(
                title := s"Fork",
                `class` := "btn", isDisabled(onClick ==> backend.fork(sid)))(
                i(`class` := "fa fa-code-fork"),
                "Fork"
              )
            )
        }
    }.build
}
