package com.olegych.scastie

import com.olegych.scastie.proto._
import com.olegych.scastie.api._
import com.olegych.scastie.client.components.Editor

import japgolly.scalajs.react.extra.Reusability

package object client {
  val isMac = dom.window.navigator.userAgent.contains("Mac")
  val ctrl = if (isMac) "⌘" else "Ctrl"

  implicit class ModalStateExtension(val modalState: ModalState) extends AnyVal {
    def isShareModalClosed(shareModalSnippetId2: SnippetId): Boolean =
      !modalState.shareModalSnippetId.contains(shareModalSnippetId2)
  }

  implicit val reusability: Reusability[View] =
    Reusability.by_==

  implicit val attachedDomsReuse: Reusability[AttachedDoms] =
    Reusability.byRef ||
      Reusability.by(_.v.keys.toSet)

  implicit val instrumentationReuse: Reusability[Set[proto.Instrumentation]] =
    Reusability.byRefOr_==

  implicit val compilationInfosReuse: Reusability[Set[Problem]] =
    Reusability.byRefOr_==

  implicit val runtimeErrorReuse: Reusability[Option[RuntimeError]] =
    Reusability.byRefOr_==

  implicit val completionsReuse: Reusability[List[EnsimeResponse.Completion]] =
    Reusability.byRefOr_==

  implicit val editorReuse: Reusability[Editor] =
    Reusability.byRef ||
      (
        Reusability.by((_: Editor).attachedDoms) &&
          Reusability.by((_: Editor).instrumentations) &&
          Reusability.by((_: Editor).compilationInfos) &&
          Reusability.by((_: Editor).runtimeError) &&
          Reusability.by((_: Editor).completions)
      )
}
