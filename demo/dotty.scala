abstract class Base {
  def message: String
}
class A extends Base {
  def message: String = "Hello"
}
class B extends Base {
  def message: String = "Dotty!"
}

class Worksheet$ {
  def helloDotty(msgs: (A | B)*): String = msgs.map(_.message).mkString(", ")

  helloDotty(new A, new B)

  1 + 1
}
