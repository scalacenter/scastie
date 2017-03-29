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

        import View.ctrl

        snippetId match {
          case None =>
            li(
              title := s"Save ($ctrl + S)",
              `class` := "btn", onClick ==> backend.save)(
              i(`class` := "fa fa-download"),
              span("Save")
            )
          case Some(sid) =>
            ul(`id` := "save-buttons")(
              li(
                title := s"Amend",
                `class` := "btn", onClick ==> backend.amend(sid))(
                i(`class` := "fa fa-pencil-square-o"),
                span("Amend")
              ),
              li(
                title := s"Save ($ctrl + S)",
                `class` := "btn", onClick ==> backend.update(sid))(
                i(`class` := "fa fa-download"),
                span("Update")
              ),
              li(
                title := s"Fork",
                `class` := "btn", onClick ==> backend.fork(sid))(
                i(`class` := "fa fa-code-fork"),
                span("Fork")
              )
            )
        }
    }.build
}
