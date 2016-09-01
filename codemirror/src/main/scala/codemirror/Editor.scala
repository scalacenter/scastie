package codemirror

import org.scalajs.dom.raw.{Element, HTMLTextAreaElement}

import scala.scalajs.js
import js.{|, UndefOr}
import js.annotation._

import scala.language.implicitConversions

@ScalaJSDefined
trait TextAreaEditor extends Editor {
  def save(): Unit
  def toTextArea(): Unit
  def getTextArea: HTMLTextAreaElement
}

@ScalaJSDefined
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
  def removeOverlay(mode: String|js.Object): Unit
  
  // use EditorEventHandler
  protected[codemirror] def on(t: String, f: js.Function): Unit

  // def on(type: String, func: (...args)): Unit
  // def off(type: String, func: (...args)): Unit
  def getDoc(): Document
  def swapDoc(document: Document): Document
  def setGutterMarker(line: Int | LineHandle, gutterID: String, value: Element): LineHandle
  def clearGutter(gutterID: String): Unit
  def lineInfo(line: Int|LineHandle): js.Object
  def addWidget(pos: Position, node: Element, scrollIntoView: Boolean): Unit
  def setSize(width: Int | String, height: Int | String): Unit
  def scrollTo(x: Int, y: Int): Unit
  // def getScrollInfo():{left, top, width, height, clientWidth, clientHeight}
  // def scrollIntoView(what: Position|{left, top, right, bottom}|{from, to}|null, ?margin: number)
  // def cursorCoords(where: Boolean | Position , mode: String):{left, top, bottom}
  // def charCoords(pos: Position, ?mode: String): {left, right, top, bottom}
  // def coordsChar(js.Object: {left, top}, ?mode: String): Position
  def lineAtHeight(height: Int, mode: UndefOr[String]): Int
  def heightAtLine(line: Int | LineHandle, mode: UndefOr[String]): Int
  def defaultTextHeight(): Int
  def defaultCharWidth(): Int
  // def getViewport():{from: number, to: number}
  def refresh(): Unit
  def getModeAt(pos: Position): js.Object
  def getTokenAt(pos: Position, precise: UndefOr[Boolean]): js.Object
  // def getLineTokens(line: Int, ?precise: Boolean): array<{start, end, String, type, state}>
  def getTokenTypeAt(pos: Position): String
  // def getHelpers(pos: Position, type: String): array<helper>
  // def getHelper(pos: Position, type: String): helper
  // def getStateAfter(?line: Int, ?precise: Boolean): js.Object
  // def operation(func: ():any): any
  def indentLine(line: Int, dir: UndefOr[String | Int]): Unit
  def toggleOverwrite(value: UndefOr[Boolean]): Unit
  def isReadOnly(): Boolean
  def execCommand(name: String): Unit
  def focus(): Unit
  def getInputField(): Element
  def getWrapperElement(): Element
  def getScrollerElement(): Element
  def getGutterElement(): Element
}

@ScalaJSDefined
trait EditorChange extends js.Object {
  val from: Position
  val to: Position
  val removed: Array[String]
  val text: Array[String]
  val origin: String
}

@ScalaJSDefined
trait BeforeEditorChange extends EditorChange {
  val canceled: Boolean
  def cancel(): Unit
  def update(from: Position, to: Position, text: String, origin: String): Unit
}

class EditorEventHandler(val editor: Editor) extends AnyVal {
  def onDelete(f: () => Unit)                                 = editor.on("delete"           , f)
  def onBeforeCursorEnter(f: () => Unit)                      = editor.on("beforeCursorEnter", f)
  def onHide(f: () => Unit)                                   = editor.on("hide"             , f)
  def onUnhide(f: () => Unit)                                 = editor.on("unhide"           , f)
  def onRedraw(f: () => Unit)                                 = editor.on("redraw"           , f)
  def onCursorActivity(f: Editor => Unit)                     = editor.on("cursorActivity"   , f)
  def onFocus(f: Editor => Unit)                              = editor.on("focus"            , f)
  def onBlur(f: Editor => Unit)                               = editor.on("blur"             , f)
  def onScroll(f: Editor => Unit)                             = editor.on("scroll"           , f)
  def onUpdate(f: Editor => Unit)                             = editor.on("update"           , f)
  def onBeforeChange(f: (Editor, BeforeEditorChange) => Unit) = editor.on("beforeChange"     , f)
  def onChange(f: (Editor, EditorChange) => Unit)             = editor.on("change"           , f)
  def onChanges(f: (Editor, Array[EditorChange]) => Unit)     = editor.on("changes"          , f)

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

trait Extensions {
  implicit def toEditorEventHandler(editor: Editor) = new EditorEventHandler(editor)
}