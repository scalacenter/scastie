import sbt._
import sbt.Build._
import sbt.Keys._

object ApplicationBuild extends Build {
  val rendererBuild = Project(id = "rendererBuild", base = file("."), settings = Defaults.defaultSettings)
}