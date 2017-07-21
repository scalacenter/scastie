package com.olegych.scastie.client.components

import com.olegych.scastie.client.View
import japgolly.scalajs.react._, vdom.all._

final case class NewButton(isNewSnippetModalClosed: Boolean,
                           openNewSnippetModal: Callback,
                           closeNewSnippetModal: Callback,
                           newSnippet: Callback) {
  @inline def render: VdomElement = NewButton.component(this)
}

object NewButton {
  def render(props: NewButton): VdomElement = {
    import View.ctrl

    li(title := s"New code snippet ($ctrl + M)",
       role := "button",
       onClick --> props.openNewSnippetModal,
       cls := "btn")(
      i(cls := "fa fa-file-o"),
      span("New"),
      PromptModal(
        modalText = "New Snippet",
        isClosed = props.isNewSnippetModalClosed,
        close = props.closeNewSnippetModal,
        actionText = "Are you sure you want to create a new snippet ?",
        actionLabel = "New",
        action = props.newSnippet
      ).render
    )
  }

  private val component =
    ScalaComponent
      .builder[NewButton]("NewButton")
      .render_P(render)
      .build
}
