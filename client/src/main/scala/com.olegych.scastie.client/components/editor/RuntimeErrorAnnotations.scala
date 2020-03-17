package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api.RuntimeError

import japgolly.scalajs.react.Callback

import codemirror.TextAreaEditor

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLDivElement

object RuntimeErrorAnnotations {
  def apply(editor: TextAreaEditor,
            currentProps: Option[Editor],
            nextProps: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {

    val doc = editor.getDoc()

    AnnotationDiff.setAnnotations[RuntimeError](
      currentProps,
      nextProps,
      state,
      modState,
      (props, _) => props.runtimeError.toSet,
      runtimeError => {
        val line = runtimeError.line.getOrElse(0)

        val icon =
          dom.document.createElement("i").asInstanceOf[HTMLDivElement]

        icon.className = "fa fa-times-circle"

        val el =
          dom.document.createElement("div").asInstanceOf[HTMLDivElement]
        el.className = "runtime-error"

        val msg = dom.document.createElement("pre")
        msg.textContent = if (runtimeError.fullStack.nonEmpty) runtimeError.fullStack else runtimeError.message

        el.appendChild(icon)
        el.appendChild(msg)

        Line(doc.addLineWidget(line - 1, el))
      },
      _.runtimeErrorAnnotations,
      (state, annotations) => state.copy(runtimeErrorAnnotations = annotations)
    )
  }
}
