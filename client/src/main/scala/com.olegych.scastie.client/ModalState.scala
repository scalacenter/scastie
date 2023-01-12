package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import japgolly.scalajs.react._
import play.api.libs.json._

object ModalState {
  implicit val formatModalState: OFormat[ModalState] = Json.format[ModalState]

  def allClosed: ModalState = ModalState(
    isHelpModalClosed = true,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true
  )

  def default: ModalState = ModalState(
    isHelpModalClosed = true,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true
  )

}

case class ModalState(
  isHelpModalClosed: Boolean,
  shareModalSnippetId: Option[SnippetId],
  isResetModalClosed: Boolean,
  isNewSnippetModalClosed: Boolean,
  isEmbeddedClosed: Boolean
) {
  val isShareModalClosed: SnippetId ~=> Boolean =
    Reusable.fn(shareModalSnippetId2 => !shareModalSnippetId.contains(shareModalSnippetId2))

}
