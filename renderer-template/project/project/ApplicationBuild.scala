import sbt._
import sbt.Build._
import sbt.Keys._

object ApplicationBuild extends Build {
  lazy val rendererBuild = Project(
    id = "rendererBuild",
    base = file("."),
    settings = Defaults.defaultSettings
  )
}
