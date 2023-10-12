
object Playground extends ScastieApp { private val instrumentationMap$ = _root_.scala.collection.mutable.Map.empty[_root_.org.scastie.api.Position, _root_.org.scastie.api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => _root_.org.scastie.api.Instrumentation(pos, r) };
class Animal:
end Animal
scala.Predef.locally {val $t = 1; instrumentationMap$(_root_.org.scastie.api.Position(25, 26)) = _root_.org.scastie.api.runtime.Runtime.render($t);$t}
}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.org.scastie.api.runtime.Runtime.write(playground.instrumentations$))
  }
}
