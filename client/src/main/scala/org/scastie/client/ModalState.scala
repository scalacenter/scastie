package org.scastie.client

import org.scastie.api.SnippetId
import io.circe._
import io.circe.generic.semiauto._

import japgolly.scalajs.react._

object ModalState {
  implicit val modalStateEncoder: Encoder[ModalState] = deriveEncoder[ModalState]
  implicit val modalStateDecoder: Decoder[ModalState] = deriveDecoder[ModalState]

  def allClosed: ModalState = ModalState(
    isHelpModalClosed = true,
    isPrivacyPolicyModalClosed = true,
    isPrivacyPolicyPromptClosed = true,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true,
    isLoginModalClosed = true
  )

  def default: ModalState = ModalState(
    isHelpModalClosed = true,
    isPrivacyPolicyModalClosed = true,
    isPrivacyPolicyPromptClosed = true,
    shareModalSnippetId = None,
    isResetModalClosed = true,
    isNewSnippetModalClosed = true,
    isEmbeddedClosed = true,
    isLoginModalClosed = true
  )
}

case class ModalState(
    isHelpModalClosed: Boolean,
    isPrivacyPolicyModalClosed: Boolean,
    @deprecated("Scheduled for removal", "2023-04-30")
    isPrivacyPolicyPromptClosed: Boolean,
    shareModalSnippetId: Option[SnippetId],
    isResetModalClosed: Boolean,
    isNewSnippetModalClosed: Boolean,
    isEmbeddedClosed: Boolean,
    isLoginModalClosed: Boolean
) {
  val isShareModalClosed: SnippetId ~=> Boolean =
    Reusable.fn(
      shareModalSnippetId2 => !shareModalSnippetId.contains(shareModalSnippetId2)
    )

}
