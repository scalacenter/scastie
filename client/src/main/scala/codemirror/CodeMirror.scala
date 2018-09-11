package codemirror

import org.scalajs.dom.raw.{Event, Element, HTMLElement, HTMLTextAreaElement}

import scala.scalajs.js
import js.annotation._
import js.{Dictionary, RegExp, UndefOr, |}

@js.native
@JSImport("codemirror", JSImport.Namespace)
object CodeMirror extends js.Object {

  @js.native
  class Pos extends js.Object {
    var line: Int = js.native
    var ch: Int = js.native
  }

  type Position = Pos

  val Pass: js.Dynamic = js.native

  var commands: js.Dynamic = js.native
  var keyMap: KeyMaps = js.native
  val Init: js.Object = js.native

  def apply(element: Element, options: UndefOr[Options]): Editor = js.native
  def fromTextArea(textarea: HTMLTextAreaElement, options: Options): TextAreaEditor = js.native
  def runMode(value: String, mode: String, destination: HTMLElement): Editor =
    js.native
  def showHint(editor: Editor, func: js.Function2[Editor, ShowHintOptions, js.Any], options: js.Any): Unit = js.native

  def defineExtension(name: String, value: js.Any): Unit = js.native
  def defineOption(
      name: String,
      default: js.Any,
      updateFunc: js.Function3[Editor, js.Any, js.Any, js.Any]
  ): Unit =
    js.native

  def on[T <: Event](obj: js.Object, t: String, f: js.Function1[T, _]): Unit =
    js.native
  def off[T <: Event](obj: js.Object, t: String, f: js.Function1[T, _]): Unit =
    js.native
}

@js.native
trait ShowHintOptions extends js.Object {
  var alignWithWord: Boolean = js.native
  var async: Boolean = js.native
  var closeOnUnfocus: Boolean = js.native
  var completeOnSingleClick: Boolean = js.native
  var completeSingle: Boolean = js.native
  var container: Element = js.native
}

trait KeyMaps extends js.Object {
  val sublime: js.Dictionary[String]
}

trait LineHandle extends js.Object

trait LineWidget extends js.Object {
  def clear(): Unit
}

trait TextMarker extends js.Object {
  def clear(): Unit
  def find(): CodeMirror.Position
  def getOptions(copyWidget: Boolean): TextMarkerOptions
}

@js.native
trait TextMarkerOptions extends js.Object {
  var className: String = js.native
  var inclusiveLeft: Boolean = js.native
  var inclusiveRight: Boolean = js.native
  var atomic: Boolean = js.native
  var collapsed: Boolean = js.native
  var clearOnEnter: Boolean = js.native
  var replacedWith: HTMLElement = js.native
  var handleMouseEvents: Boolean = js.native
  var readOnly: Boolean = js.native
  var addToHistory: Boolean = js.native
  var startStyle: String = js.native
  var endStyle: String = js.native
  var shared: Boolean = js.native
}

trait Options extends js.Object {
  val value: UndefOr[String | Document]
  val mode: UndefOr[String | js.Object]
  val lineSeparator: UndefOr[String]
  val theme: UndefOr[String]
  val indentUnit: UndefOr[Int]
  val smartIndent: UndefOr[Boolean]
  val tabSize: UndefOr[Int]
  val indentWithTabs: UndefOr[Boolean]
  val electricChars: UndefOr[Boolean]
  val specialChars: UndefOr[RegExp]
  val specialCharPlaceholder: UndefOr[Char => Element]
  val rtlMoveVisually: UndefOr[Boolean]
  val keyMap: UndefOr[String]
  val extraKeys: UndefOr[Dictionary[String]]
  val lineWrapping: UndefOr[Boolean]
  val lineNumbers: UndefOr[Boolean]
  val firstLineNumber: UndefOr[Int]
  val lineNumberFormatter: UndefOr[Int => String]
  val gutters: UndefOr[Array[String]]
  val fixedGutter: UndefOr[Boolean]
  val scrollbarStyle: UndefOr[String]
  val coverGutterNextToScrollbar: UndefOr[Boolean]
  val inputStyle: UndefOr[String]
  val readOnly: UndefOr[Boolean | String]
  val showCursorWhenSelecting: UndefOr[Boolean]
  val lineWiseCopyCut: UndefOr[Boolean]
  val undoDepth: UndefOr[Int]
  val historyEventDelay: UndefOr[Int]
  val tabindex: UndefOr[Int]
  val autofocus: UndefOr[Boolean]
  val dragDrop: UndefOr[Boolean]
  val allowDropFileTypes: UndefOr[Array[String]]
  val cursorBlinkRate: UndefOr[Double]
  val cursorScrollMargin: UndefOr[Double]
  val cursorHeight: UndefOr[Double]
  val resetSelectionOnContextMenu: UndefOr[Boolean]
  val workTime: UndefOr[Double]
  val workDelay: UndefOr[Double]
  val pollInterval: UndefOr[Double]
  val flattenSpans: UndefOr[Boolean]
  val addModeClass: UndefOr[Boolean]
  val maxHighlightLength: UndefOr[Double]
  val viewportMargin: UndefOr[Int]
}
