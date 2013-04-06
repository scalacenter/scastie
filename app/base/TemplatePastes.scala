package base

import com.olegych.scastie.PastesActor.Paste
import java.util.concurrent.atomic.AtomicLong
import com.olegych.scastie.PastesActor

/**
  */
object TemplatePastes {
  private val pasteIds = new AtomicLong(1L) {
    def next = incrementAndGet()
  }

  val default = nextPaste( """
/***
scalaVersion := "2.10.1"

*/
object Main extends App {
}
                           """)

  val templates = {
    List(
      "scalaz" -> nextPaste( """
/***
scalaVersion := "2.10.1"

libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "7.0.0-RC1")
*/
import scalaz._, Scalaz._
object Main extends App {
  println(List(some(1), none).suml)
}
                             """)
      ,
      "play&akka" -> nextPaste( """
/***
scalaVersion := "2.10.1"

libraryDependencies ++= Seq("play" %% "play" % "2.1.1")
*/
import play.api._
import play.api.mvc._
object Main extends App {
  import play.api.data.Form
  import play.api.data.Forms._
  case class A(i: Int)
  val f = Form(mapping("i" -> number)(A.apply)(A.unapply))
  println(f.fill(A(1)).get)
}
                                """)
      ,
      "2.9" -> nextPaste( """
/***
scalaVersion := "2.9.3"

*/
object Main extends App {
}
                          """)
    )
  }

  def nextPaste(x: String): PastesActor.Paste = {
    Paste(pasteIds.next, Some(x), None, None)
  }
}
