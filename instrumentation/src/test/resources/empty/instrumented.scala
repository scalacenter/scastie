import _root_.com.olegych.scastie.api.runtime._
object Playground extends ScastieApp with _root_.com.olegych.scastie.api.InstrumentationRecorder {

}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.com.olegych.scastie.api.runtime.Runtime.writeStatements(playground.$doc.getResults()))
  }
}
