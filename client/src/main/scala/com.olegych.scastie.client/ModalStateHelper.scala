package com.olegych.scastie.client

import com.olegych.scastie.proto.SnippetId
import com.olegych.scastie.proto._

object ModalStateHelper {
  def allClosed =
    ModalState(
      isWelcomeModalClosed = true,
      isHelpModalClosed = true,
      None,
      isResetModalClosed = true,
      isNewSnippetModalClosed = true
    )

  def default = 
    ModalState(
      isHelpModalClosed = true,
      isWelcomeModalClosed = false,
      shareModalSnippetId = None,
      isResetModalClosed = true,
      isNewSnippetModalClosed = true
    )
}