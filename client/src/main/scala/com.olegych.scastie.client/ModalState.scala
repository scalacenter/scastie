package com.olegych.scastie.client

object ModalState {
  def default = ModalState(
    isHelpModalClosed = true,
    isWelcomeModalClosed = false,
    isShareModalClosed = true,
    isResetModalClosed = true
  )
}

case class ModalState(
   isWelcomeModalClosed: Boolean,
   isHelpModalClosed: Boolean,
   isShareModalClosed: Boolean,
   isResetModalClosed: Boolean)


