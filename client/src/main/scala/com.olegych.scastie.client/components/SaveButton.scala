package com.olegych.scastie.client
package components

import com.olegych.scastie.api.{SnippetId, User}

import japgolly.scalajs.react._, vdom.all._

final case class SaveButton(isSnippetSaved: Boolean,
                            inputsHasChanged: Boolean,
                            user: Option[User],
                            snippetId: Option[SnippetId],
                            amend: SnippetId => Callback,
                            update: SnippetId => Callback,
                            save: Callback,
                            fork: SnippetId => Callback) {
  @inline def render: VdomElement = SaveButton.component(this)
}

object SaveButton {
  def render(props: SaveButton): VdomElement = {

    val disabledIfSaved =
      (cls := "disabled").when(props.isSnippetSaved)

    val disabledIfInputsHasNotChanged =
      (cls := "disabled").when(!props.inputsHasChanged)

    def userFunctions(sid: SnippetId): TagMod =
      TagMod(
        li(title := "Amend this code snippet",
           cls := "btn",
           disabledIfSaved,
           onClick --> props.amend(sid))(
          i(cls := "fa fa-pencil-square-o"),
          span("Amend")
        ),
        li(title := s"Save as a new updated version ($ctrl + S)",
           cls := "btn",
           disabledIfSaved,
           onClick --> props.update(sid))(
          i(cls := "fa fa-download"),
          span("Update")
        )
      ).when(sid.isOwnedBy(props.user))

    props.snippetId match {
      case Some(sid) if props.isSnippetSaved =>
        li(
          ul(cls := "save-buttons")(
            userFunctions(sid),
            li(title := "Save as a new forked code snippet",
               cls := "btn",
               disabledIfInputsHasNotChanged,
               onClick --> props.fork(sid))(
              i(cls := "fa fa-code-fork"),
              span("Fork")
            )
          )
        )
      case _ =>
        li(title := s"Save ($ctrl + S)",
           role := "button",
           cls := "btn",
           disabledIfSaved,
           onClick --> props.save)(
          i(cls := "fa fa-download"),
          span("Save")
        )
    }
  }

  private val component =
    ScalaComponent
      .builder[SaveButton]("SaveButton")
      .render_P(render)
      .build
}
