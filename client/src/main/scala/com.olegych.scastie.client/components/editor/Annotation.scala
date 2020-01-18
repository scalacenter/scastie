package com.olegych.scastie.client.components.editor

import codemirror.{TextMarker, TextMarkerOptions, LineWidget, TextAreaEditor}
import codemirror.CodeMirror.{Pos => CMPosition}

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, HTMLPreElement, HTMLDivElement}

import scala.scalajs.js

private[editor] sealed trait Annotation {
  def clear(): Unit
}

private[editor] case class Line(lw: LineWidget) extends Annotation {
  def clear(): Unit = lw.clear()
}

private[editor] case class Marked(tm: TextMarker) extends Annotation {
  def clear(): Unit = tm.clear()
}

private[editor] case object Empty extends Annotation {
  def clear(): Unit = ()
}

object Annotation {
  def nextline(editor: TextAreaEditor,
               endPos: CMPosition,
               content: String,
               process: (HTMLElement => Unit),
               options: js.Any = null): Annotation = {
    val node =
      dom.document.createElement("pre").asInstanceOf[HTMLPreElement]
    node.className = "line"
    node.innerHTML = content

    process(node)
    Line(editor.addLineWidget(endPos.line, node, options))
  }

  def inline(editor: TextAreaEditor, startPos: CMPosition, content: String, process: (HTMLElement => Unit)): Annotation = {

    // inspired by blink/devtools WebInspector.JavaScriptSourceFrame::_renderDecorations

    val node =
      dom.document.createElement("pre").asInstanceOf[HTMLPreElement]

    node.className = "inline"

    def updateLeft(editor2: codemirror.Editor): Unit = {
      val doc2 = editor2.getDoc()
      val lineNumber = startPos.line
      doc2.getLine(lineNumber).toOption match {
        case Some(line) =>
          val basePos = new CMPosition { line = lineNumber; ch = 0 }
          val offsetPos = new CMPosition {
            line = lineNumber
            ch = doc2.getLine(lineNumber).map(_.length).getOrElse(0)
          }
          val mode = "local"
          val base = editor2.cursorCoords(basePos, mode)
          val offset = editor2.cursorCoords(offsetPos, mode)
          node.style.left = (offset.left - base.left).toString + "px"
        case _ =>
          // the line was deleted
          node.innerHTML = null
      }
    }
    updateLeft(editor)
    editor.onChange((editor, _) => updateLeft(editor))

    node.innerHTML = content
    process(node)

    Line(editor.addLineWidget(startPos.line, node, null))
  }

  def fold(editor: TextAreaEditor, startPos: CMPosition, endPos: CMPosition, content: String, process: (HTMLElement => Unit)): Annotation = {

    val node =
      dom.document.createElement("div").asInstanceOf[HTMLDivElement]
    node.className = "fold"
    node.innerHTML = content
    process(node)
    Marked(
      editor
        .getDoc()
        .markText(
          startPos,
          endPos,
          js.Dictionary[Any](
              "replacedWith" -> node,
              "handleMouseEvents" -> true
            )
            .asInstanceOf[TextMarkerOptions]
        )
    )
  }
}
