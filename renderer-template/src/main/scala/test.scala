/***
scalaVersion := "2.10.1"

libraryDependencies ++= Seq("org.scalaz" %% "scalaz-core" % "7.0.0",
                            "com.chuusai" %% "shapeless" % "1.2.4",
                            "org.spire-math" %% "spire" % "0.4.0")
*/

object Main extends App {
  import scala.concurrent._
  import ExecutionContext.Implicits.global
  println(future { Thread.sleep(10 * 1000); println("hello") })

}

