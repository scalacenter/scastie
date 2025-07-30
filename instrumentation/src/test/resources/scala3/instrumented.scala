import _root_.com.olegych.scastie.api.runtime.*
object Playground extends ScastieApp with _root_.com.olegych.scastie.api.InstrumentationRecorder {
class Animal:
end Animal
scala.Predef.locally {
$doc.startStatement(25, 26);
val $t = 1; 
$doc.binder(_root_.com.olegych.scastie.api.runtime.Runtime.render($t), 25, 26);
$doc.endStatement();
$t}

scala.Predef.locally {
$doc.startStatement(28, 49);
val $t = println:
    "animal"; 
$doc.binder(_root_.com.olegych.scastie.api.runtime.Runtime.render($t), 28, 49);
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
