package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._
import com.olegych.scastie.api

import japgolly.scalajs.react.Reusability

object EditorReusability {
  implicit val reusability: Reusability[Editor] =
    Reusability.derive[Editor]

  implicit val problemAnnotationsReuse: Reusability[Map[api.Problem, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val renderAnnotationsReuse: Reusability[Map[api.Instrumentation, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val runtimeErrorAnnotationsReuse: Reusability[Map[api.RuntimeError, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val codeFoldsReuse: Reusability[Map[RangePosititon, Annotation]] =
    Reusability((a, b) => a.keys == b.keys)

  implicit val editorStateReuse: Reusability[EditorState] =
    Reusability.caseClassExcept[EditorState](Symbol("editor"))
}
