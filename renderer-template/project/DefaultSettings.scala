import sbt.Keys._
import sbt._

object DefaultSettings {
  def apply: Seq[Setting[_]] =
    Seq(
      scalacOptions,
      traceLevel := 1000,
      crossPaths := false,
      crossTarget := file("target")
    )

  def scalacOptions: Def.Setting[Task[Seq[String]]] = {
    Keys.scalacOptions <++= (scalaSource in Compile,
                             baseDirectory,
                             scalaVersion) map {
      (scalaSource, baseDirectory, scalaVersion) =>
        val featureOptions =
          if (Is210(scalaVersion) || Is211(scalaVersion)) List("-feature")
          else Nil
        val warnAll = if (Is210(scalaVersion)) List("-Ywarn-all") else Nil
        List("-deprecation", "-unchecked", "-Xcheckinit") ++
          featureOptions ++
          warnAll
    }
  }

  class IsStartsWith(startsWith: String) {
    def apply(scalaVersion: String) = scalaVersion.startsWith(startsWith)
    def unapply(scalaVersion: String): Option[String] =
      Some(scalaVersion).filter(apply)
  }

  object Is29  extends IsStartsWith("2.9")
  object Is210 extends IsStartsWith("2.10")
  object Is211 extends IsStartsWith("2.11")

  def addSupportedCompilerPlugin(
      version: PartialFunction[String, ModuleID]): Def.Setting[Seq[ModuleID]] =
    libraryDependencies <++= scalaVersion { scalaVersion =>
      version.lift(scalaVersion).map(compilerPlugin).toList
    }
}
