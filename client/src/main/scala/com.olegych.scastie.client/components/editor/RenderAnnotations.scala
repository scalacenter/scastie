package com.olegych.scastie.client.components.editor

import codemirror.{CodeMirror, TextAreaEditor, modeScala}
import com.olegych.scastie.api
import com.olegych.scastie.client.HTMLFormatter
import japgolly.scalajs.react.Callback
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

object RenderAnnotations {
  def apply(editor: TextAreaEditor,
            currentProps: Option[Editor],
            nextProps: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {

    val doc = editor.getDoc()

    AnnotationDiff.setAnnotations[api.Instrumentation](
      currentProps,
      nextProps,
      state,
      modState,
      (props, _) => props.instrumentations, {
        case api.Instrumentation(api.Position(start, end), api.Value(value, tpe)) =>
          val startPos = doc.posFromIndex(start)
          val endPos = doc.posFromIndex(end)
          val isString = tpe == "String"
          val htmlValue = if (isString) HTMLFormatter.format(value) else value
          val process = (node: HTMLElement) => {
            if (!isString) CodeMirror.runMode(s"$value: $tpe", modeScala, node)
            node.title = tpe
            ()
          }
          if (value.contains('\n'))
            Annotation.nextline(editor, endPos, htmlValue, process)
          else Annotation.inline(editor, startPos, htmlValue, process)

        case api.Instrumentation(api.Position(start, end), api.Html(content, folded)) =>
          val startPos = doc.posFromIndex(start)
          val endPos = doc.posFromIndex(end)

          val process: HTMLElement => Unit = _.innerHTML = content
          if (!folded) Annotation.nextline(editor, endPos, content, process)
          else Annotation.fold(editor, startPos, endPos, content, process)

        case instrumentation @ api.Instrumentation(
              api.Position(start, end),
              api.AttachedDom(uuid, folded)
            ) =>
          val startPos = doc.posFromIndex(start)
          val endPos = doc.posFromIndex(end)

          val domNode = nextProps.attachedDoms.get(uuid)

          if (domNode.isDefined) {
            val process: HTMLElement => Unit = element => {
              domNode.foreach(element.appendChild)
              ()
            }

            if (!folded) Annotation.nextline(editor, endPos, "", process)
            else Annotation.fold(editor, startPos, endPos, "", process)

          } else {
            dom.console.log("cannot find dom element uuid: " + uuid)
            Empty
          }

      },
      _.renderAnnotations,
      (state, annotations) => state.copy(renderAnnotations = annotations)
    )
  }
}
