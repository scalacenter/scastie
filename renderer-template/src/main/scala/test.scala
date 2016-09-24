/***
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  "com.scalakata" %% "annotation" % "1.1.5"
)

resolvers += "masseguillaume" at "http://dl.bintray.com/content/masseguillaume/maven"
*/

import com.scalakata._
object Main {
  def main(args: Array[String]): Unit = {
    val p = new Playground
    println(p.instrumentation$)
  }
}

@instrument
class Playground {
  1
}