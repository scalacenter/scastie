object Main {
  val worksheet = new Worksheet$
  def main(args: Array[String]): Unit = {
    println(worksheet.instrumentations$)
  }
}
class Worksheet$ {
  private val instrumentationMap$ = scala.collection.mutable.Map.empty[api.Position, api.Render]
  def instrumentations$ = instrumentationMap$.toList.map({
    case (pos, r) => api.Instrumentation(pos, r)
  })
  {
    val t = println(1)
    instrumentationMap$(api.Position(21, 31)) = Runtime.render(t)
    t
  }
  if (true) {
    val t = 42
    instrumentationMap$(api.Position(46, 48)) = Runtime.render(t)
    t
  } else {
    val t = 45
    instrumentationMap$(api.Position(54, 56)) = Runtime.render(t)
    t
  }
  {
    val t = for (i <- List(1, 2, 3)) yield i + 1
    instrumentationMap$(api.Position(62, 100)) = Runtime.render(t)
    t
  }
  val a = {
    val t = 1 + 1
    instrumentationMap$(api.Position(112, 117)) = Runtime.render(t)
    t
  }
  var b = {
    val t = 2 + 2
    instrumentationMap$(api.Position(131, 136)) = Runtime.render(t)
    t
  }
  b = {
    val t = 4 + 4
    instrumentationMap$(api.Position(144, 149)) = Runtime.render(t)
    t
  }
  {
    val t = implicitly[Ordering[Int]].cmp(1, 2)
    instrumentationMap$(api.Position(153, 188)) = Runtime.render(t)
    t
  }
}
