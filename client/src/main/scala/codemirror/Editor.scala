package codemirror

import org.scalajs.dom.raw.{Element, HTMLElement, HTMLTextAreaElement}

import scala.scalajs.js
import org.scalajs.dom

import js.{UndefOr, |}
import scala.language.implicitConversions

trait TextAreaEditor extends Editor {
  def save(): Unit
  def toTextArea(): Unit
  def getTextArea: HTMLTextAreaElement
}

trait ScrollInfo extends js.Object {
  val left: Double
  val top: Double
  val width: Double
  val height: Double
  val clientWidth: Double
  val clientHeight: Double
}

trait Editor extends js.Object {
  // def replaceSelection(spaces)
  def hasFocus(): Boolean
  // def findPositionH(start: Position, amount: Int, unit: String, visually: Boolean):{line, ch, ?hitSide: Boolean}
  // def findPositionV(start: Position, amount: Int, unit: String):{line, ch, ?hitSide: Boolean}
  // def findWordAt(pos: Position):{anchor: Position, head: Position}
  def setOption(option: String, value: js.Any): Unit
  def getOption(option: String): js.Any
  def addKeyMap(map: js.Object, bottom: Boolean): Unit
  def removeKeyMap(map: js.Object): Unit
  def addOverlay(mode: String | js.Object, options: UndefOr[js.Object]): Unit
  def removeOverlay(mode: String | js.Object): Unit

  // use EditorEventHandler
  protected[codemirror] def on(t: String, f: js.Function): Unit

  // def on(type: String, func: (...args)): Unit
  // def off(type: String, func: (...args)): Unit
  def getDoc(): Document
  def swapDoc(document: Document): Document
  def setGutterMarker(line: Int | LineHandle, gutterID: String, value: Element): LineHandle
  def clearGutter(gutterID: String): Unit
  def lineInfo(line: Int | LineHandle): js.Object
  def addWidget(pos: Position, node: Element, scrollIntoView: Boolean): Unit
  def addLineWidget(line: Int, node: HTMLElement, options: UndefOr[js.Any]): LineWidget
  def setSize(width: Int | String, height: Int | String): Unit
  def scrollTo(x: Double, y: Double): Unit
  def getScrollInfo(): ScrollInfo
  // def scrollIntoView(what: Position|{left, top, right, bottom}|{from, to}|null, ?margin: number)
  def cursorCoords(where: Boolean | Position, mode: String): CursorCoords
  // def charCoords(pos: Position, ?mode: String): {left, right, top, bottom}
  def coordsChar(where: js.Dictionary[Any], mode: UndefOr[String]): Position
  def lineAtHeight(height: Int, mode: UndefOr[String]): Int
  def heightAtLine(line: Int | LineHandle, mode: UndefOr[String]): Int
  def defaultTextHeight(): Int
  def defaultCharWidth(): Int
  // def getViewport():{from: number, to: number}
  def refresh(): Unit
  def getModeAt(pos: Position): js.Object
  def getTokenAt(pos: Position, precise: UndefOr[Boolean]): Token
  // def getLineTokens(line: Int, ?precise: Boolean): array<{start, end, String, type, state}>
  def getTokenTypeAt(pos: Position): String
  // def getHelpers(pos: Position, type: String): array<helper>
  // def getHelper(pos: Position, type: String): helper
  // def getStateAfter(?line: Int, ?precise: Boolean): js.Object
  // def operation(func: ():any): any
  def indentLine(line: Int, dir: UndefOr[String | Int]): Unit
  def indentSelection(command: String): Unit
  def toggleOverwrite(value: UndefOr[Boolean]): Unit
  def isReadOnly(): Boolean
  def execCommand(name: String): Unit
  def focus(): Unit
  def getInputField(): Element
  def getWrapperElement(): Element
  def getScrollerElement(): Element
  def getGutterElement(): Element
  def somethingSelected(): Boolean
}

trait Token extends js.Object {
  val string: String
}

trait EditorChange extends js.Object {
  val from: Position
  val to: Position
  val removed: Array[String]
  val text: Array[String]
  val origin: String
}

trait BeforeEditorChange extends EditorChange {
  val canceled: Boolean
  def cancel(): Unit
  def update(from: Position, to: Position, text: String, origin: String): Unit
}

class EditorEventHandler(val editor: Editor) extends AnyVal {
  def onDelete(f: () => Unit): Unit = editor.on("delete", f)
  def onBeforeCursorEnter(f: () => Unit): Unit =
    editor.on("beforeCursorEnter", f)
  def onHide(f: () => Unit): Unit = editor.on("hide", f)
  def onUnhide(f: () => Unit): Unit = editor.on("unhide", f)
  def onRedraw(f: () => Unit): Unit = editor.on("redraw", f)
  def onCursorActivity(f: Editor => Unit): Unit = editor.on("cursorActivity", f)
  def onFocus(f: Editor => Unit): Unit = editor.on("focus", f)
  def onBlur(f: Editor => Unit): Unit = editor.on("blur", f)
  def onScroll(f: Editor => Unit): Unit = editor.on("scroll", f)
  def onUpdate(f: Editor => Unit): Unit = editor.on("update", f)
  def onBeforeChange(f: (Editor, BeforeEditorChange) => Unit): Unit =
    editor.on("beforeChange", f)
  def onChange(f: (Editor, EditorChange) => Unit): Unit = editor.on("change", f)
  def onChanges(f: (Editor, js.Array[EditorChange]) => Unit): Unit =
    editor.on("changes", f)
  def onKeyUp(f: (Editor, dom.KeyboardEvent) => Unit): Unit =
    editor.on("keyup", f)
  def onKeyDown(f: (Editor, dom.KeyboardEvent) => Unit): Unit =
    editor.on("keydown", f)
  def onMouseDown(f: (Editor, dom.MouseEvent) => Unit): Unit =
    editor.on("mousedown", f)

// "clear"                    (from: {line, ch}, to: {line, ch})
// "change"                   (CodeMirror, changeObj: object)
// "changes"                  (CodeMirror, changes: array<object>)
// "change"                   (doc: CodeMirror.Doc, changeObj: object)

// "keyHandled"               (CodeMirror, name: string, event: Event)
// "inputRead"                (CodeMirror, changeObj: object)
// "electricInput"            (CodeMirror, line: integer)
// "beforeSelectionChange"    (CodeMirror, obj: {ranges, origin, update})
// "viewportChange"           (CodeMirror, from: number, to: number)
// "swapDoc"                  (CodeMirror, oldDoc: Doc)
// "gutterClick"              (CodeMirror, line: integer, gutter: string, clickEvent: Event)
// "gutterContextMenu"        (CodeMirror, line: integer, gutter: string, contextMenu: Event: Event)
// "scrollCursorIntoView"     (CodeMirror, event: Event)
// "renderLine"               (CodeMirror, line: LineHandle, element: Element)
// "beforeSelectionChange"    (doc: CodeMirror.Doc, selection: {head, anchor})

// (CodeMirror, dom event)
  // "mousedown",
  // "dblclick",
  // "touchstart",
  // "contextmenu",
  // "keydown",
  // "keypress",
  // "keyup",
  // "cut",
  // "copy",
  // "paste",
  // "dragstart",
  // "dragenter",
  // "dragover",
  // "dragleave",
  // "drop"
}

trait CursorCoords extends js.Object {
  val left: Double
  val top: Double
  val bottom: Double
}

trait Extensions {
  implicit def toEditorEventHandler(editor: Editor): EditorEventHandler =
    new EditorEventHandler(editor)
}
