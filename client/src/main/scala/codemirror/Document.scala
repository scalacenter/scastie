package codemirror

import scala.scalajs.js
import js.{|, UndefOr, undefined}
import js.annotation._
import org.scalajs.dom.raw.Element

@js.native
@JSGlobal("Doc")
class Document protected () extends js.Object {
  def getValue(separator: UndefOr[String] = undefined): String = js.native
  def setValue(content: String): Unit = js.native
  def getRange(from: Position, to: Position, separator: UndefOr[String]): String = js.native
  def replaceRange(replacement: String, from: Position, to: Position, origin: UndefOr[String]): Unit = js.native
  def getLine(n: Int): UndefOr[String] = js.native
  def lineCount(): Int = js.native
  def firstLine(): Int = js.native
  def lastLine(): Int = js.native
  def getLineHandle(num: Int): LineHandle = js.native
  def getLineNumber(handle: LineHandle): Int = js.native
  // def eachLine(f: (line: LineHandle)): Unit = js.native
  // def eachLine(start: Int, end: Int, f: (line: LineHandle)): Unit = js.native
  def markClean(): Unit = js.native
  def changeGeneration(closeEvent: UndefOr[Boolean]): Int = js.native
  def isClean(generation: UndefOr[Int]): Boolean = js.native
  def getSelection(lineSep: UndefOr[String]): String = js.native
  def getSelections(lineSep: UndefOr[String]): Array[String] = js.native
  def replaceSelection(replacement: String, select: UndefOr[String] = undefined): Unit =
    js.native
  def replaceSelections(replacements: Array[String], select: UndefOr[String] = undefined): Unit = js.native
  def getCursor(start: UndefOr[String] = undefined): Position = js.native
  // def listSelections(): array<{anchor, head}> = js.native
  def somethingSelected(): Boolean = js.native
  def setCursor(pos: Position | Int, ch: UndefOr[Int] = undefined, options: UndefOr[js.Object] = undefined): Unit = js.native
  def setSelection(anchor: Position, head: UndefOr[Position], options: UndefOr[js.Object]): Unit = js.native
  // def setSelections(ranges: array<{anchor, head}>, primary: UndefOr[Int], options: UndefOr[js.Object]): Unit = js.native
  def addSelection(anchor: Position, head: UndefOr[Position]): Unit = js.native
  def extendSelection(from: Position, to: UndefOr[Position], options: UndefOr[js.Object]): Unit = js.native
  def extendSelections(heads: Array[Position], options: UndefOr[js.Object]): Unit = js.native
  // def extendSelectionsBy(f: function(range: {anchor, head}):Position), ? = js.native
  def setExtending(value: Boolean): Unit = js.native
  def getExtending(): Boolean = js.native
  def getEditor(): Editor = js.native
  def copy(copyHistory: Boolean): Document = js.native
  def linkedDoc(options: js.Object): Document = js.native
  def unlinkDoc(doc: Document): Unit = js.native
  def iterLinkedDocs(function: (Document, Boolean) => Unit): Unit = js.native
  def undo(): Unit = js.native
  def redo(): Unit = js.native
  def undoSelection(): Unit = js.native
  def redoSelection(): Unit = js.native
  // def historySize(): {undo: Int, redo: Int} = js.native
  def clearHistory(): Unit = js.native
  def getHistory(): js.Object = js.native
  def setHistory(history: js.Object): Unit = js.native
  def markText(from: Position, to: Position, options: UndefOr[js.Object]): TextMarker = js.native
  def setBookmark(pos: Position, options: UndefOr[js.Object]): TextMarker =
    js.native
  def findMarks(from: Position, to: Position): Array[TextMarker] = js.native
  def findMarksAt(pos: Position): Array[TextMarker] = js.native
  def getAllMarks(): Array[TextMarker] = js.native
  def addLineClass(line: Int | LineHandle, where: String, `class`: String): LineHandle = js.native
  def removeLineClass(line: Int | LineHandle, where: String, `class`: String): LineHandle = js.native
  def addLineWidget(line: Int | LineHandle, node: Element, options: UndefOr[js.Object] = undefined): LineWidget =
    js.native
  def setGutterMarker(line: Int | LineHandle, gutterID: String, value: Element): LineWidget = js.native
  def getMode(): js.Object = js.native
  def lineSeparator(): Unit = js.native
  def posFromIndex(index: Int): Position = js.native
  def indexFromPos(pos: Position): Int = js.native
}
