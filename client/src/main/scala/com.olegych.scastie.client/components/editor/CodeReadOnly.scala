package com.olegych.scastie.client.components.editor

import codemirror.TextAreaEditor
import codemirror.TextMarkerOptions
import japgolly.scalajs.react.Callback

import scala.scalajs.js

/**
 * Allows to make part of code readonly.
 * Marks:
 * - `// read-only` - start of readonly code
 * - `// end-read-only` - end of readonly code
 *
 * Comments are immutable itself, so mark after adding cannot be removed.
 */
object CodeReadOnly {
  private def findReadonlyComments(code: String): Set[RangePosititon] = {
    val (readOnlyPositions, _, _) = {
      val lines = code.split("\n").toList

      lines.foldLeft((Set.empty[RangePosititon], Option.empty[Int], 0)) {
        case ((readOnlyPositions, open, indexTotal), line) =>
          val (readOnlyPositions0, open0) =
            if (line.trim matches """\/\/(\s*)read-only""") {
              if (open.isEmpty) (readOnlyPositions, Some(indexTotal))
              else (readOnlyPositions, open)
            } else if (line.trim matches """\/\/(\s*)end-read-only(.*)""") {
              open match {
                case Some(start) => (readOnlyPositions + RangePosititon(start, indexTotal + line.length), None)
                case None => (readOnlyPositions, None)
              }
            } else {
              (readOnlyPositions, open)
            }

          (readOnlyPositions0, open0, indexTotal + line.length + 1)
      }
    }

    readOnlyPositions
  }

  def markReadOnly(editor: TextAreaEditor, props: Editor, modState: (EditorState => EditorState) => Callback): Callback = {
    modState { state =>
      if (!state.readOnly && props.code.nonEmpty) {
        val doc = editor.getDoc()
        findReadonlyComments(props.code).foreach { range =>
          val posStart = doc.posFromIndex(range.indexStart)
          val posEnd = doc.posFromIndex(range.indexEnd)

          val options = js.Dictionary[Any](
            "readOnly" -> true,
            "className" -> "readOnly"
          ).asInstanceOf[TextMarkerOptions]

          doc.markText(posStart, posEnd, options)
        }
        state.copy(readOnly = true)
      } else state
    }
  }

}
