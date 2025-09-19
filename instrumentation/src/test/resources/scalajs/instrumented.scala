import _root_.org.scastie.runtime._
object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder with _root_.org.scastie.runtime.DomHook {
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

scala.Predef.locally {
$doc.startStatement(59, 116);
val $t = dom.document.createElement("canvas").asInstanceOf[Canvas];
$doc.binder(_root_.org.scastie.runtime.Runtime.render($t, attach _), 59, 116);
$doc.endStatement();
$t}
}
@_root_.scala.scalajs.js.annotation.JSExportTopLevel("ScastiePlaygroundMain") class ScastiePlaygroundMain {
  def suppressUnusedWarnsScastie = Html
  val playground = _root_.org.scastie.runtime.api.RuntimeError.wrap(Playground)
  @_root_.scala.scalajs.js.annotation.JSExport def result = playground match {
    case Right(p) => 
      p.main(Array())
      _root_.org.scastie.runtime.Runtime.writeStatements(p.$doc.getResults())
    case Left(error) => _root_.org.scastie.runtime.Runtime.writeStatements(List())
  }
  @_root_.scala.scalajs.js.annotation.JSExport def attachedElements: _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement] =
    playground match {
      case Right(p) => p.attachedElements
      case Left(_) => _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement]()
    }
}