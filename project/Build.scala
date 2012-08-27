import sbt._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "scastie"
  val appVersion = "1.0"

  val appDependencies = Seq(
    // Add your project dependencies here,
  )

  val renderer = Project(id = "renderer", base = file("renderer"),
    settings = Defaults.defaultSettings ++ PlayProject.intellijCommandSettings("SCALA") ++ Seq(
      Keys.libraryDependencies ++= Seq(
        "com.typesafe.akka" % "akka-actor" % "2.0.3",
        "com.typesafe.akka" % "akka-slf4j" % "2.0.3",
        "org.apache.commons" % "commons-lang3" % "3.1"
      )
    ))

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here
  ) dependsOn (renderer) aggregate (renderer)

}
