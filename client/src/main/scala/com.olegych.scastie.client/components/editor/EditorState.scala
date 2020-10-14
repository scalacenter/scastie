package com.olegych.scastie.client.components.editor

import codemirror.TextAreaEditor
import com.olegych.scastie.api

private[editor] case class EditorState(
    editor: Option[TextAreaEditor] = None,
    problemAnnotations: Map[api.Problem, Annotation] = Map(),
    renderAnnotations: Map[api.Instrumentation, Annotation] = Map(),
    runtimeErrorAnnotations: Map[api.RuntimeError, Annotation] = Map(),
    folded: Boolean = false,
)
