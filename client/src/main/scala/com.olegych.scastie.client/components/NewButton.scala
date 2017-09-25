package com.olegych.scastie.client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class NewButton(isNewSnippetModalClosed: Boolean,
                           openNewSnippetModal: Reusable[Callback],
                           closeNewSnippetModal: Reusable[Callback],
                           newSnippet: Reusable[Callback]) {
  @inline def render: VdomElement = NewButton.component(this)
}

object NewButton {
  implicit val reusability: Reusability[NewButton] =
    Reusability.caseClass[NewButton]

  def render(props: NewButton): VdomElement = {

    li(title := s"New code snippet",
       role := "button",
       onClick --> props.openNewSnippetModal,
       cls := "btn")(
      i(cls := "fa fa-file-o"),
      span("New"),
      PromptModal(
        modalText = "New Snippet",
        modalId = "new-snippet-modal",
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
      .configure(Reusability.shouldComponentUpdate)
      .build
}
