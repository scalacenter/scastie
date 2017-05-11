package com.olegych.scastie
package client

import api.SnippetId

object ModalState {
  def allClosed = ModalState(true, true, None, true, true)

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
    shareModalSnippetId != Some(shareModalSnippetId2)
}
