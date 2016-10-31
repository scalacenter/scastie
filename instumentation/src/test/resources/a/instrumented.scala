object Main {
  val worksheet = new Worksheet$
  def main(args: Array[String]): Unit = {
    println(worksheet.instrumentation$)
  }
}
class Worksheet$ {
  private val instrumentation$ = scala.collection.mutable.Map.empty[(Int, Int), (String, String)]
  def instrumentation$ = instrumentation$.toList
  val a = {
    val t = 1 + 1
    instrumentation$((29, 34)) = render(t)
    t
  }
}
