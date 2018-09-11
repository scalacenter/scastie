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
                  (folds + RangePosititon(start, indexTotal + line.length), None)

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

  def apply(editor: TextAreaEditor, props: Editor): Callback = {
    Callback {
      val doc = editor.getDoc()
      findFolds(props.code).foreach { range =>
        val posStart = doc.posFromIndex(range.indexStart)
        val posEnd = doc.posFromIndex(range.indexEnd)

        var annotRef: Option[Annotation] = None
        val unfold: (HTMLElement => Unit) = { element =>
          def clear(event: dom.Event): Unit = {
            annotRef.foreach(_.clear())
          }

          element.className = element.className + " code-fold"
          element.addEventListener("touchstart", clear _, false)
          element.addEventListener("dblclick", clear _, false)
          element.addEventListener("click", clear _, false)
        }
        val annot = Annotation.fold(
          editor,
          posStart,
          posEnd,
          "-- Click to unfold --",
          unfold
        )

        annotRef = Some(annot)

        (range, annot)
      }
    }
  }
}
