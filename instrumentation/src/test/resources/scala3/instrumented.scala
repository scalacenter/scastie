import _root_.org.scastie.runtime.*
object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
class Animal:
end Animal
scala.Predef.locally {
$doc.startStatement(25, 30);
val $t = 1 + 2;
$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 25, 30);
$doc.endStatement();
$t}



scala.Predef.locally {
$doc.startStatement(34, 39);
val $t = 1 + 5;
$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 34, 39);
$doc.endStatement();
$t}
scala.Predef.locally {
$doc.startStatement(40, 61);
val $t = println:
    "animal";
$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 40, 61);
$doc.endStatement();
$t}
}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.org.scastie.runtime.Runtime.writeStatements(playground.$doc.getResults()))
  }
}
