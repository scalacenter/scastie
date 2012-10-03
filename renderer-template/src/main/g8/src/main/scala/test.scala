/***
libraryDependencies ++= Seq(
  "net.databinder" % "dispatch-twitter_2.9.0-1" % "0.8.3",
  "net.databinder" % "dispatch-http_2.9.0-1" % "0.8.3"
)
*/
object test extends App {
  println("hello")
  new java.io.File("d:/hello").delete
  println("done")
}