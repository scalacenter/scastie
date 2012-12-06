/***
scalaVersion := "ololo"

libraryDependencies ++= Seq("play" % "play_2.9.1" % "2.0.4")
*/
object Main extends App {
  import play.api.data.Form
  import play.api.data.Forms._
  case class A(i: Int)
  val f = Form(mapping("i" -> number)(A.apply)(A.unapply))
  println(f.fill(A(1)).get)
}
object X extends App {
  println("hello")
}
