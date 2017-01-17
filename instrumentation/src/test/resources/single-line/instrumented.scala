class Playground { private val instrumentationMap$ = scala.collection.mutable.Map.empty[api.Position, api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => api.Instrumentation(pos, r) }; locally {val t = 1 + 1; instrumentationMap$(api.Position(19, 24)) = scastie.runtime.Runtime.render(t);t} }
object Main {
  val playground = new Playground
  def main(args: Array[String]): Unit = {
    println(scastie.runtime.Runtime.write(playground.instrumentations$))
  }
}
