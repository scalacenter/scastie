package com.olegych.scastie.client.components.editor

import codemirror.TextAreaEditor

import japgolly.scalajs.react.Callback

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

object CodeFoldingAnnotations {
  private def findFolds(code: String): Set[RangePosititon] = {
    val (folds, _, _) = {
      val lines = code.split("\n").toList

      lines.foldLeft((Set.empty[RangePosititon], Option.empty[Int], 0)) {
        case ((folds, open, indexTotal), line) => {
          val (folds0, open0) =
            if (line == "// fold") {
              if (open.isEmpty) (folds, Some(indexTotal))
              else (folds, open)
            } else if (line == "// end-fold") {
              open match {
                case Some(start) =>
                  (folds + RangePosititon(start, indexTotal + line.length),
                   None)

                case None => (folds, None)
              }
            } else {
              (folds, open)
            }

          (folds0, open0, indexTotal + line.length + 1)
        }
      }
    }

    folds
  }

  def apply(editor: TextAreaEditor,
            current: Option[Editor],
            next: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {

    val codeChanged = current.map(_.code != next.code).getOrElse(true)

    val doc = editor.getDoc()

    AnnotationDiff.setAnnotations[RangePosititon](
      current,
      next,
      state,
      modState,
      (props, state) => {
        if (current.contains(props)) {
          // code folds are already calculated
          state.codeFoldsAnnotations.keySet
        } else {
          findFolds(props.code) -- state.unfoldedCode
        }
      },
      range => {
        val posStart = doc.posFromIndex(range.indexStart)
        val posEnd = doc.posFromIndex(range.indexEnd)

        val noop: (HTMLElement => Unit) = element => {
          element.onclick = (event: dom.MouseEvent) => {
            // TODO
            // direct.modState(state =>
            //   state.copy(
            //     unfoldedCode = state.unfoldedCode + range
            //   )
            // )
          }
        }

        Annotation.fold(editor, posStart, posEnd, "", noop)
      },
      _.codeFoldsAnnotations,
      (state, annotations) => state.copy(codeFoldsAnnotations = annotations)
    ).when_(codeChanged)
  }
}
