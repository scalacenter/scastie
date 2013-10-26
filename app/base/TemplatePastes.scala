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
scalaVersion := "2.10.3"

*/
object Main extends App {
}
                           """)

  val templates = {
    List(
      "typelevel" -> nextPaste( """
/***
scalaVersion := "2.10.3"

libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "7.0.4",
                            "org.typelevel" %% "shapeless-spire" % "0.1.2",
                            "org.typelevel" %% "shapeless-scalaz" % "0.1.2"
                            )
*/
object Main extends App {
  import scalaz._, Scalaz._
  println(List(some(1), none).suml)

  import shapeless._
  object combine extends Poly2 {
    implicit def caseCharString = at[Char, String]((c, s) => s.indexOf(c))
    implicit def caseIntBoolean = at[Int, Boolean]((i, b) => if ((i >= 0) == b) "pass" else "fail")
  }
  val l1 = "foo" :: true :: HNil
  val f1 = l1.foldLeft('o')(combine)
  println(f1)

  import spire.math._ // provides functions, types, and type classes
  import spire.implicits._ // provides infix operators, instances and conversions
  import spire.random._
  val rng = Cmwc5()
  implicit val nextmap = Dist.map[Int, Complex[Double]](2, 5)
  val m = rng.next[Map[Int, Complex[Double]]]
  println(m)
}
                                             """)
      ,
      "typesafe" -> nextPaste( """
/***
scalaVersion := "2.10.3"

libraryDependencies ++= Seq("com.typesafe.play" %% "play" % "2.2.1-RC1")
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
      "sbt 0.13" -> nextPaste( """
/***
sbtPlugin := true
*/
import sbt._
object Build extends Build {
  val p = project.settings(Keys.scalaVersion := Keys.name.value)
}
                          """)
      ,
      "scala 2.9" -> nextPaste( """
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
