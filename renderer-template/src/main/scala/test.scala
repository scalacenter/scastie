/***
scalaVersion := "2.10.0"

libraryDependencies ++= Seq("org.scala-lang" % "scala-compiler" % "2.10.0")
*/
package controllers

/**
 */
package object evil {

  import scala.reflect.runtime._ //hello
  import scala.reflect.runtime.universe._
  import scala.tools.reflect.ToolBox

  class Foo

  val toolBox = universe.runtimeMirror(getClass.getClassLoader).mkToolBox()

  def membersOf[T](implicit tag: TypeTag[T]) = tag.tpe.members

  def members(clazz: String) = {
    import toolBox._
    eval(parse(s"controllers.evil.membersOf[$clazz]"))
  }

}

case object Main extends App {
  println(evil.members("controllers.evil.Foo"))
}