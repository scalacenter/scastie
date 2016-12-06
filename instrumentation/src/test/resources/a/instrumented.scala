object Main {
  val worksheet = new Worksheet$
  def main(args: Array[String]): Unit = {
    println(scastie.runtime.Runtime.write(worksheet.instrumentations$))
  }
}
class Worksheet$ {
  private val instrumentationMap$ = scala.collection.mutable.Map.empty[api.Position, api.Render]
  def instrumentations$ = {
    instrumentationMap$.toList.map({
      case (pos, r) => api.Instrumentation(pos, r)
    })
  }
  {
    val t = println(1)
    instrumentationMap$(api.Position(21, 31)) = scastie.runtime.Runtime.render(t)
    t
  }
  if (true) {
    {
      val t = 42
      instrumentationMap$(api.Position(45, 47)) = scastie.runtime.Runtime.render(t)
      t
    }
  } else {
    {
      val t = 45
      instrumentationMap$(api.Position(53, 55)) = scastie.runtime.Runtime.render(t)
      t
    }
  }
  {
    val t = for (i <- List(1, 2, 3)) yield i + 1
    instrumentationMap$(api.Position(59, 97)) = scastie.runtime.Runtime.render(t)
    t
  }
  val a = {
    val t = 1 + 1
    instrumentationMap$(api.Position(109, 114)) = scastie.runtime.Runtime.render(t)
    t
  }
  var b = {
    val t = 2 + 2
    instrumentationMap$(api.Position(126, 131)) = scastie.runtime.Runtime.render(t)
    t
  }
  b = {
    val t = 4 + 4
    instrumentationMap$(api.Position(139, 144)) = scastie.runtime.Runtime.render(t)
    t
  }
  {
    val t = implicitly[Ordering[Int]].cmp(1, 2)
    instrumentationMap$(api.Position(148, 183)) = scastie.runtime.Runtime.render(t)
    t
  }
}
