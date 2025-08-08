package com.olegych.scastie.client
package components

import com.olegych.scastie.client.components.editor.EditorKeymaps
import japgolly.scalajs.react._

import com.olegych.scastie.client.i18n.I18n

import vdom.all._
import japgolly.scalajs.react.hooks.HookCtx.I18

final case class NewButton(
                           isDarkTheme: Boolean,
                           isNewSnippetModalClosed: Boolean,
                           openNewSnippetModal: Reusable[Callback],
                           closeNewSnippetModal: Reusable[Callback],
                           newSnippet: Reusable[Callback],
                           language: String) {
  @inline def render: VdomElement = NewButton.component(this)
}

object NewButton {
  implicit val reusability: Reusability[NewButton] =
    Reusability.derive[NewButton]

  def render(props: NewButton): VdomElement = {

    li(
      title := s"${I18n.t("New code snippet")} (${EditorKeymaps.openNewSnippetModal.getName})",
      role := "button",
      onClick --> props.openNewSnippetModal,
      cls := "btn"
    )(
      i(cls := "fa fa-file-o"),
      span(I18n.t("New")),
      PromptModal(
        isDarkTheme = props.isDarkTheme,
        modalText = I18n.t("New Snippet"),
        modalId = "new-snippet-modal",
        isClosed = props.isNewSnippetModalClosed,
        close = props.closeNewSnippetModal,
        actionText = I18n.t("Are you sure you want to create a new snippet?"),
        actionLabel = I18n.t("New"),
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
