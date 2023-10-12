import _root_.org.scastie.api.runtime._
object Playground extends ScastieApp { private val instrumentationMap$ = _root_.scala.collection.mutable.Map.empty[_root_.org.scastie.api.Position, _root_.org.scastie.api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => _root_.org.scastie.api.Instrumentation(pos, r) };
scala.Predef.locally {val $t = 1 + 1; instrumentationMap$(_root_.org.scastie.api.Position(0, 5)) = _root_.org.scastie.api.runtime.Runtime.render($t);$t}

scala.Predef.locally {val $t = 1 +
  a; instrumentationMap$(_root_.org.scastie.api.Position(7, 14)) = _root_.org.scastie.api.runtime.Runtime.render($t);$t}
}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.org.scastie.api.runtime.Runtime.write(playground.instrumentations$))
  }
}
