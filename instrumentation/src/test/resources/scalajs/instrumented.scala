import _root_.org.scastie.runtime._
object Playground extends ScastieApp with _root_.org.scastie.runtime.InstrumentationRecorder with _root_.org.scastie.runtime.DomHook {
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

scala.Predef.locally {
$doc.startStatement(59, 116);
val $t = dom.document.createElement("canvas").asInstanceOf[Canvas];
$doc.binder(_root_.org.scastie.runtime.Runtime.render($t, attach), 59, 116);
$doc.endStatement();
$t}
}
@_root_.scala.scalajs.js.annotation.JSExportTopLevel("ScastiePlaygroundMain") class ScastiePlaygroundMain {
  def suppressUnusedWarnsScastie = Html
  val playground = _root_.org.scastie.runtime.api.RuntimeError.wrap(Playground)
  @_root_.scala.scalajs.js.annotation.JSExport def result = _root_.org.scastie.runtime.Runtime.writeStatements(playground.map { playground => playground.main(Array()); playground.$doc.getResults() })
  @_root_.scala.scalajs.js.annotation.JSExport def attachedElements: _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.HTMLElement] =
    playground match {
      case Right(p) => p.attachedElements
      case Left(_) => _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.HTMLElement]()
    }
}