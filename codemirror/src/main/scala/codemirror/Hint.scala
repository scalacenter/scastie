package codemirror

import org.querki.jsext.{JSOptionBuilder, OptMap, noOpts}
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js

/**
 * This facade originally written by @MasseGuillaume.
 */
@js.native
trait Hint extends js.Object {
  // The completion text. This is the only required property.
  var text: String = js.native

  // The text that should be displayed in the menu.
  var displayText: String = js.native

  // A CSS class name to apply to the completion's line in the menu.
  var className: String = js.native

  // A method used to create the DOM structure for showing the completion by appending it to its first argument.
  var render: js.Function3[HTMLElement, Hint, js.Array[js.Any], Unit] =
    js.native

  // A method used to actually apply the completion, instead of the default behavior.
  var hint: js.Function3[HTMLElement, Hint, js.Array[js.Any], Unit] = js.native

  // Optional from position that will be used by pick() instead of the global one passed with the full list of completions.
  var from: Position = js.native
  var to: Position = js.native
}

object HintConfig extends HintConfig(noOpts)
class HintConfig(val dict: OptMap)
    extends JSOptionBuilder[Hint, HintConfig](new HintConfig(_)) {
  def text(v: String) = jsOpt("text", v)
  def displayText(v: String) = jsOpt("displayText", v)
  def className(v: String) = jsOpt("className", v)
  def render(v: js.Function3[HTMLElement, Hint, js.Array[js.Any], Unit]) =
    jsOpt("render", v)
  def hint(v: js.Function3[HTMLElement, Hint, js.Array[js.Any], Unit]) =
    jsOpt("hint", v)
  def from(v: Position) = jsOpt("from", v)
  def to(v: Position) = jsOpt("to", v)
}
