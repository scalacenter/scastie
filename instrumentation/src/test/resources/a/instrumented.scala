class Worksheet$ { private val instrumentationMap$ = scala.collection.mutable.Map.empty[api.Position, api.Render];def instrumentations$ = instrumentationMap$.toList.map{ case (pos, r) => api.Instrumentation(pos, r) }
  val a = locally {val t = 1 + 1;instrumentationMap$(api.Position(29, 34)) = scastie.runtime.Runtime.render(t);t}

  locally {val t = 1 +
  a;instrumentationMap$(api.Position(38, 45)) = scastie.runtime.Runtime.render(t);t}
}
object Main {
  val worksheet = new Worksheet$
  def main(args: Array[String]): Unit = {
    println(scastie.runtime.Runtime.write(worksheet.instrumentations$))
  }
}
