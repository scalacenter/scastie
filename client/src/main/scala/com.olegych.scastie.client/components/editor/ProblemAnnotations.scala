package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.client.AnsiColorFormatter

import codemirror.TextAreaEditor

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLDivElement

import japgolly.scalajs.react.Callback

object ProblemAnnotations {

  def apply(editor: TextAreaEditor,
            currentProps: Option[Editor],
            nextProps: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {

    val doc = editor.getDoc()

    AnnotationDiff.setAnnotations[api.Problem](
      currentProps,
      nextProps,
      state,
      modState,
      (props, _) => props.compilationInfos,
      info => {
        val line = info.line.getOrElse(0)

        val icon =
          dom.document.createElement("i").asInstanceOf[HTMLDivElement]

        val iconSeverity =
          info.severity match {
            case api.Info    => "fa fa-info"
            case api.Warning => "fa fa-exclamation-triangle"
            case api.Error   => "fa fa-times-circle"
          }

        val classSeverity =
          info.severity match {
            case api.Info    => "info"
            case api.Warning => "warning"
            case api.Error   => "error"
          }

        icon.className = iconSeverity

        val el =
          dom.document.createElement("div").asInstanceOf[HTMLDivElement]
        el.className = s"compilation-info $classSeverity"

        val msg = dom.document.createElement("pre")

        msg.innerHTML = AnsiColorFormatter.formatToHtml(info.message)

        el.appendChild(icon)
        el.appendChild(msg)

        Line(doc.addLineWidget(line - 1, el))
      },
      _.problemAnnotations,
      (state, annotations) => state.copy(problemAnnotations = annotations)
    )
  }
}
