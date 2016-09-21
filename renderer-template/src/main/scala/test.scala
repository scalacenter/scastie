/***
scalaVersion := "0.1-SNAPSHOT"
scalaOrganization := "ch.epfl.lamp"
scalacOptions ++= Seq("-language:Scala2")
scalaBinaryVersion := "2.11"
autoScalaLibrary := false
libraryDependencies += "org.scala-lang" % "scala-library" % "2.11.5"
scalaCompilerBridgeSource := ("ch.epfl.lamp" % "dotty-bridge" % "0.1.1-SNAPSHOT" % "component").sources()
*/

object Example {
  def main(args: Array[String]): Unit = {
    e1
  }
  trait A
  trait B

  trait Wr {
    val z: A with B
  }
}