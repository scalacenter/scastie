package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId

import play.api.libs.json._

object ModalState {
  implicit val formatModalState: OFormat[ModalState] = Json.format[ModalState]

  def allClosed =
    ModalState(isWelcomeModalClosed = true,
               isHelpModalClosed = true,
               None,
               isResetModalClosed = true,
               isNewSnippetModalClosed = true)

  def default = ModalState(
    isHelpModalClosed = true,
    isWelcomeModalClosed = false,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true
  )
}

case class ModalState(
    isWelcomeModalClosed: Boolean,
    isHelpModalClosed: Boolean,
    shareModalSnippetId: Option[SnippetId],
    isResetModalClosed: Boolean,
    isNewSnippetModalClosed: Boolean
) {
  def isShareModalClosed(shareModalSnippetId2: SnippetId): Boolean =
    !shareModalSnippetId.contains(shareModalSnippetId2)
}
