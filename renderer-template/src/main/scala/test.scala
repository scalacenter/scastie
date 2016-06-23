/***
//scalaVersion := "2.12.0-M4"
coursier.CoursierPlugin.projectSettings
//com.felixmulder.dotty.plugin.DottyPlugin.projectSettings
sbtPlugin := true
addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")
fork := true
libraryDependencies ++= {
  val scalazVersion = "7.2.2"
  val fs2Version = "0.9.0-M3"
  val spireVersion = "0.11.0"
  Seq(
//    "org.scalaz" %% "scalaz-core" % scalazVersion,
//    "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
//    "org.spire-math" %% "spire" % spireVersion,
//    "co.fs2" %% "fs2-core" % fs2Version,
//    "co.fs2" %% "fs2-io" % fs2Version
  )
}
*/
object Main extends sbt.Build with App {
  //  import fs2.{ io, text }
  //  import fs2.util.Task
  //  import fs2.{ io, text }
  //  import fs2.util.Task
  //  import java.nio.file.Paths
  //
  //  def fahrenheitToCelsius(f: Double): Double =
  //    (f - 32.0) * (5.0 / 9.0)
  //
  //  val converter: Task[Unit] =
  //    io.file.readAll[Task](Paths.get("build.sbt"), 4096)
  //      .through(text.utf8Decode)
  //      .through(text.lines)
  //      .filter(s => !s.trim.isEmpty && !s.startsWith("//"))
  //      .map(line => fahrenheitToCelsius(line.getBytes.sum).toString)
  //      .intersperse("\n")
  //      .through(text.utf8Encode)
  //      .run
  //
  //  // at the end of the universe...
  //  val u: Unit = converter.unsafeRun
}
