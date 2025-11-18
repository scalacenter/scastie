package org.scastie.client.components.editor

import typings.codemirrorState.mod.ChangeSet
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.webTreeSitter.mod._

import scala.collection.mutable.ListBuffer

import scalajs.js
import scala.collection.mutable.TreeMap

class SyntaxHighlightingHandler(parser: Parser, language: Language, query: Query, initialState: String) extends js.Object {
  val queryCaptureNames = query.captureNames

  var tree = parser.parse(initialState).asInstanceOf[Tree]
  var decorations: DecorationSet = computeDecorations()

  private def computeDecorations(): DecorationSet = {
    val rangeSetBuilder = new RangeSetBuilder[Decoration]()
    val captures = query.captures(tree.rootNode)

    // Group captures by their range - for identical ranges, keep only the last (highest priority)
    val capturesByRange = captures.foldLeft(Map.empty[(Double, Double), QueryCapture]) { (acc, capture) =>
      val range = (capture.node.startIndex, capture.node.endIndex)
      acc + (range -> capture)
    }

    // Build non-overlapping segments by splitting parent ranges
    val segments = TreeMap.empty[Double, (Double, String)]
    val sortedBySize = capturesByRange.toSeq.sortBy { case ((start, end), _) => end - start }

    sortedBySize.foreach { case ((start, end), capture) =>
      val overlapping = segments.rangeTo(end).filter { case (segStart, (segEnd, _)) =>
        segEnd > start
      }

      var currentPos = start

      overlapping.foreach { case (segStart, (segEnd, _)) =>
        if (currentPos < segStart) {
          segments += (currentPos -> (segStart, capture.name))
        }
        currentPos = currentPos max segEnd
      }

      if (currentPos < end) {
        segments += (currentPos -> (end, capture.name))
      }
    }

    segments.foreach { case (startPos, (endPos, captureName)) =>
      val mark = Decoration.mark(
        MarkDecorationSpec()
          .setInclusive(true)
          .setClass(captureName.replace(".", "-")))

      rangeSetBuilder.add(startPos, endPos, mark)
    }

    rangeSetBuilder.finish()
  }

  private def indexToTSPoint(text: Text, index: Double): Point = {
    val line = text.lineAt(index)
    Point(index - line.from, line.number - 1)
  }

  private def mapChangesToTSEdits(changes: ChangeSet, originalText: Text, newText: Text): List[Edit] = {
    val editBuffer = new ListBuffer[Edit]()

    changes.iterChanges { (fromA: Double, toA: Double, _, toB: Double, _) => {
      val oldEndPosition = indexToTSPoint(originalText, toA)
      val newEndPosition = indexToTSPoint(newText, toB)
      val startPosition = indexToTSPoint(originalText, fromA)
      editBuffer.addOne(Edit(toB, newEndPosition, toA, oldEndPosition, fromA, startPosition))
    }}

    editBuffer.toList

  }

  var update: js.Function1[ViewUpdate, Unit] = viewUpdate => {
    if (viewUpdate.docChanged) {
      val newText = viewUpdate.state.doc.toString
      val edits = mapChangesToTSEdits(viewUpdate.changes, viewUpdate.startState.doc, viewUpdate.state.doc)
      edits.foreach(tree.edit)
      tree = parser.parse(newText, tree)
      decorations = computeDecorations()
    }
  }
}
