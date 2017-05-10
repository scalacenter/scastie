package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object SaveButton {

  def apply(state: AppState,
            backend: AppBackend,
            snippetId: Option[SnippetId]) =
    component((state, backend, snippetId))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend, Option[SnippetId])]("SaveButton")
      .render_P {
        case (state, backend, snippetId) =>
          val disabledIfSaved =
            if (state.isSnippetSaved) "disabled"
            else ""

          import View.ctrl

          def userFunctions(sid: SnippetId) =
            if(state.user.isDefined) {
              TagMod(
                li(title := "Amend this code snippet",
                   `class` := s"btn $disabledIfSaved",
                   onClick ==> backend.amend(sid))(
                  i(`class` := "fa fa-pencil-square-o"),
                  span("Amend")
                ),
                li(title := s"Save as a new updated version ($ctrl + S)",
                   `class` := s"btn $disabledIfSaved",
                   onClick ==> backend.update(sid))(
                  i(`class` := "fa fa-download"),
                  span("Update")
                )
              )
            }
            else EmptyVdom

          snippetId match {
            case None =>
              li(title := s"Save ($ctrl + S)",
                 role := "button",
                 `class` := s"btn $disabledIfSaved",
                 onClick ==> backend.save)(
                i(`class` := "fa fa-download"),
                span("Save")
              )
            case Some(sid) =>
              ul(`class` := "save-buttons")(
                userFunctions(sid),
                li(title := "Save as a new forked code snippet",
                   `class` := s"btn $disabledIfSaved",
                   onClick ==> backend.fork(sid))(
                  i(`class` := "fa fa-code-fork"),
                  span("Fork")
                )
              )
          }
      }
      .build
}
