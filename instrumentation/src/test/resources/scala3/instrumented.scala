import _root_.org.scastie.runtime.*
object Playground extends ScastieApp with _root_.org.scastie.runtime.InstrumentationRecorder {
class Animal:
end Animal
scala.Predef.locally {
$doc.startStatement(25, 30);
val _t = 1 + 2;
$doc.binder(_root_.org.scastie.runtime.Runtime.render(_t), 25, 30);
$doc.endStatement();
_t}



scala.Predef.locally {
$doc.startStatement(34, 39);
val _t = 1 + 5;
$doc.binder(_root_.org.scastie.runtime.Runtime.render(_t), 34, 39);
$doc.endStatement();
_t}
scala.Predef.locally {
$doc.startStatement(40, 61);
val _t = println:
    "animal";
$doc.binder(_root_.org.scastie.runtime.Runtime.render(_t), 40, 61);
$doc.endStatement();
_t}
}
object Main {
  def suppressUnusedWarnsScastie = Html
  val playground = Playground
  def main(args: Array[String]): Unit = {
    playground.main(Array())
    scala.Predef.println("\n" + _root_.org.scastie.runtime.Runtime.writeStatements(playground.$doc.getResults()))
  }
}
