abstract class Base {
  def message: String
}
class A extends Base {
  def message: String = "Hello"
}
class B extends Base {
  def message: String = "Scala 3!"
}

def helloScala3(msgs: (A | B)*): String = msgs.map(_.message).mkString(", ")

helloScala3(new A, new B)

1 + 1
