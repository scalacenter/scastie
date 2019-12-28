package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId

import play.api.libs.json._

import japgolly.scalajs.react._

object ModalState {
  implicit val formatModalState: OFormat[ModalState] = Json.format[ModalState]

  def allClosed: ModalState = ModalState(
    isWelcomeModalClosed = true,
    isHelpModalClosed = true,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true
  )

  def default: ModalState = ModalState(
    isHelpModalClosed = true,
    isWelcomeModalClosed = false,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true
  )
}

case class ModalState(
    isWelcomeModalClosed: Boolean,
    isHelpModalClosed: Boolean,
    shareModalSnippetId: Option[SnippetId],
    isResetModalClosed: Boolean,
    isNewSnippetModalClosed: Boolean,
    isEmbeddedClosed: Boolean
) {
  val isShareModalClosed: SnippetId ~=> Boolean =
    Reusable.fn(
      shareModalSnippetId2 => !shareModalSnippetId.contains(shareModalSnippetId2)
    )

}
