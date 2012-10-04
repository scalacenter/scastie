object Main extends App {
/***
scalaVersion := "ololo"

libraryDependencies ++= Seq(
  "net.databinder" % "dispatch-twitter_2.9.0-1" % "0.8.3",
  "net.databinder" % "dispatch-http_2.9.0-1" % "0.8.3"
)
*/

  import dispatch.{ json, Http, Request }
  import dispatch.twitter.Search
  import json.{ Js, JsObject }

  def process(param: JsObject) = {
    val Search.text(txt) = param
    val Search.from_user(usr) = param
    val Search.created_at(time) = param

    "(" + time + ")" + usr + ": " + txt
  }

  Http.x((Search("#scala") lang "en") ~> (_ map process foreach println))
}
object X extends App {
  println("hello")
}