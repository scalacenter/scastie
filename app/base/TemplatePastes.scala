// package base

// import com.olegych.scastie.PastesActor.Paste
// import java.util.concurrent.atomic.AtomicLong
// import com.olegych.scastie.PastesActor

// /**
//   */
// object TemplatePastes {
//   class PasteId extends AtomicLong(1L) {
//     def next = incrementAndGet()
//   }

//   private val pasteIds = new PasteId()

//   val default = nextPaste(
// """
// coursier.CoursierPlugin.projectSettings
// scalaVersion := "2.11.8"
// """,
// """
// object Main {
//   def main(args: Array[String]) {

//   }
// }
// """
// )

//   val templates = {
//     List(
//       "typelevel" -> nextPaste(
// """
// coursier.CoursierPlugin.projectSettings
// scalaVersion := "2.11.8"
// addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
// libraryDependencies ++= {
//   val scalazVersion = "7.2.2"
//   val fs2Version = "0.9.0-M3"
//   val shapelessVersion = "2.2.5"
//   val monocleVersion = "1.2.1"
//   val spireVersion = "0.11.0"
//   Seq(
//     "org.scalaz" %% "scalaz-core" % scalazVersion,
//     "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
//     "com.chuusai" %% "shapeless" % shapelessVersion,
//     "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion,
//     "com.github.julien-truffaut" %% "monocle-law" % monocleVersion,
//     "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
//     "org.spire-math" %% "spire" % spireVersion,
//     "co.fs2" %% "fs2-core" % fs2Version,
//     "co.fs2" %% "fs2-io" % fs2Version
//   )
// }
// """,
// """
// import scalaz._, Scalaz._
// import shapeless._
// import spire.math._
// import spire.implicits._
// import spire.random._
// import fs2.{io, text}
// import fs2.util.Task
// import monocle._
// import monocle.syntax._
// import monocle.std.string._
// object Main {
//   def main(args: Array[String]) {

//   }
// }
// """)
//       ,
//       "typesafe" -> nextPaste(
// """
// coursier.CoursierPlugin.projectSettings
// scalaVersion := "2.11.8"
// libraryDependencies ++= Seq("com.typesafe.play" %% "play" % "2.5.3")
// """,
// """
// import play.api
// import akka.actor
// object Main {
//   def main(args: Array[String]) {

//   }
// }
// """)
//       ,
//       "sbt 0.13" -> nextPaste(
// """
// coursier.CoursierPlugin.projectSettings
// sbtPlugin := true
// """,
// """
// import sbt._
// import Keys._
// object Main extends Build {
//   def main(args: Array[String]) {

//   }
// }
// """)
//       ,
//       "scala 2.12" -> nextPaste(
// """
// coursier.CoursierPlugin.projectSettings
// scalaVersion := "2.12.0-RC2"
// """,
// """
// object Main {
//   def main(args: Array[String]) {

//   }
// }
// """)
//       ,
//       "dotty" -> nextPaste(
// """
// com.felixmulder.dotty.plugin.DottyPlugin.projectSettings
// """,
// """
// object Main {
//   def main(args: Array[String]) {

//   }
// }
// """)
//     )
//   }

//   def nextPaste(code: String, sbtConfig: String): PastesActor.Paste =
//     Paste(pasteIds.next, Some(code), Some(sbtConfig), None, Seq(), None, None)
// }
