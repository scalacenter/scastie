package com.olegych.scastie
package client
package components

import api.SnippetId

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

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
                   clazz := s"btn $disabledIfSaved",
                   onClick ==> backend.amend(sid))(
                  i(clazz := "fa fa-pencil-square-o"),
                  span("Amend")
                ),
                li(title := s"Save as a new updated version ($ctrl + S)",
                   clazz := s"btn $disabledIfSaved",
                   onClick ==> backend.update(sid))(
                  i(clazz := "fa fa-download"),
                  span("Update")
                )
              )
            }
            else EmptyVdom

          snippetId match {
            case None =>
              li(title := s"Save ($ctrl + S)",
                 role := "button",
                 clazz := s"btn $disabledIfSaved",
                 onClick ==> backend.save)(
                i(clazz := "fa fa-download"),
                span("Save")
              )
            case Some(sid) =>
              ul(clazz := "save-buttons")(
                userFunctions(sid),
                li(title := "Save as a new forked code snippet",
                   clazz := s"btn $disabledIfSaved",
                   onClick ==> backend.fork(sid))(
                  i(clazz := "fa fa-code-fork"),
                  span("Fork")
                )
              )
          }
      }
      .build
}
