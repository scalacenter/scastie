import _root_.com.olegych.scastie.api.runtime._
class Playground { private val instrumentationMap$ = _root_.scala.collection.mutable.Map.empty[_root_.com.olegych.scastie.api.Position, _root_.com.olegych.scastie.api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => _root_.com.olegych.scastie.api.Instrumentation(pos, r) };
val a = locally {val t = 1 + 1; instrumentationMap$(_root_.com.olegych.scastie.api.Position(8, 13)) = _root_.com.olegych.scastie.api.runtime.Runtime.render(t);t}

locally {val t = 1 +
  a; instrumentationMap$(_root_.com.olegych.scastie.api.Position(15, 22)) = _root_.com.olegych.scastie.api.runtime.Runtime.render(t);t}
}
object Main {
  val playground = new Playground
  def main(args: Array[String]): Unit = {
    println(_root_.com.olegych.scastie.api.runtime.Runtime.write(playground.instrumentations$))
  }
}
