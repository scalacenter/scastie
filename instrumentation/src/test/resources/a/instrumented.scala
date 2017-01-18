import api.runtime._
class Playground { private val instrumentationMap$ = scala.collection.mutable.Map.empty[api.Position, api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => api.Instrumentation(pos, r) };
val a = locally {val t = 1 + 1; instrumentationMap$(api.Position(8, 13)) = scastie.runtime.Runtime.render(t);t}

locally {val t = 1 +
  a; instrumentationMap$(api.Position(15, 22)) = scastie.runtime.Runtime.render(t);t}
}
object Main {
  val playground = new Playground
  def main(args: Array[String]): Unit = {
    println(scastie.runtime.Runtime.write(playground.instrumentations$))
  }
}
