import _root_.org.scastie.runtime._
object Playground extends ScastieApp with _root_.org.scastie.runtime.InstrumentationRecorder {

}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.org.scastie.runtime.Runtime.writeStatements(playground.$doc.getResults()))
  }
}
