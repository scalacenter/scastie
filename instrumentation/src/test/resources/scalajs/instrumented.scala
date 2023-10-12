import _root_.org.scastie.api.runtime._
object Playground extends ScastieApp with _root_.org.scastie.api.runtime.DomHook { private val instrumentationMap$ = _root_.scala.collection.mutable.Map.empty[_root_.org.scastie.api.Position, _root_.org.scastie.api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => _root_.org.scastie.api.Instrumentation(pos, r) };
import org.scalajs.dom
import org.scalajs.dom.html.Canvas

scala.Predef.locally {val $t = dom.document.createElement("canvas").asInstanceOf[Canvas]; instrumentationMap$(_root_.org.scastie.api.Position(59, 116)) = _root_.org.scastie.api.runtime.Runtime.render($t, attach _);$t}
}
@_root_.scala.scalajs.js.annotation.JSExportTopLevel("ScastiePlaygroundMain") class ScastiePlaygroundMain {
  def suppressUnusedWarnsScastie = Html
  val playground = _root_.org.scastie.api.RuntimeError.wrap(Playground)
  @_root_.scala.scalajs.js.annotation.JSExport def result = _root_.org.scastie.api.runtime.Runtime.write(playground.map{ playground => playground.main(Array()); playground.instrumentations$ })
  @_root_.scala.scalajs.js.annotation.JSExport def attachedElements: _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement] =
    playground match {
      case Right(p) => p.attachedElements
      case Left(_) => _root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement]()
    }
}
