import _root_.com.olegych.scastie.api.runtime._
object Playground extends ScastieApp with _root_.com.olegych.scastie.api.InstrumentationRecorder {
scala.Predef.locally {
  $doc.startStatement(0, 5);
  val $t = 1 + 1;
  $doc.binder(_root_.com.olegych.scastie.api.runtime.Runtime.render($t), 0, 5);
  $doc.endStatement();
  $t}
scala.Predef.locally {
  $doc.startStatement(7, 14);
  val $t = 1 +
  a;
  $doc.binder(_root_.com.olegych.scastie.api.runtime.Runtime.render($t), 7, 14);
  $doc.endStatement();
  $t}
}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.com.olegych.scastie.api.runtime.Runtime.writeStatements(playground.$doc.getResults()))
  }
}
