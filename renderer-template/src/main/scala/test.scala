/***
sbtPlugin := true
*/
import sbt._
object Build extends Build {
  val p = project.settings(Keys.scalaVersion := Keys.name.value)
}
