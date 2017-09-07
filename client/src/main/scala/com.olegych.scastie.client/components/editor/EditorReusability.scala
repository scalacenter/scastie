package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._
import com.olegych.scastie.api

import japgolly.scalajs.react.extra.Reusability

object EditorReusability {
  implicit val reusability: Reusability[Editor] =
    Reusability.caseClass[Editor]

  implicit val problemAnnotationsReuse
    : Reusability[Map[api.Problem, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val renderAnnotationsReuse
    : Reusability[Map[api.Instrumentation, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val runtimeErrorAnnotationsReuse
    : Reusability[Map[api.RuntimeError, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val codeFoldsReuse: Reusability[Map[RangePosititon, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val completionStateReuse: Reusability[CompletionState] =
    Reusability.byRefOr_==

  implicit val loadingMessageReuse: Reusability[LoadingMessage] =
    Reusability.by((_: LoadingMessage).isVisible)

  implicit val hoverMessageReuse: Reusability[HoverMessage] =
    Reusability.by((_: HoverMessage).getMessage)

  implicit val editorStateReuse: Reusability[EditorState] =
    Reusability.caseClassExcept[EditorState]('editor)
}
