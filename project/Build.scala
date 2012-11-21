import sbt._
import PlayProject._
import com.typesafe.startscript.StartScriptPlugin

object ApplicationBuild extends Build {

  val appName = "scastie"
  val appVersion = "1.0"

  val appDependencies = Seq(
    // Add your project dependencies here,
  )

  val renderer = {
    val akkaVersion = "2.0.4"
    Project(id = "renderer", base = file("renderer"),
      settings = Defaults.defaultSettings ++ PlayProject.intellijCommandSettings("SCALA") ++ Seq(
        Keys.libraryDependencies ++= Seq(
          "com.typesafe.akka" % "akka-actor" % akkaVersion,
          "com.typesafe.akka" % "akka-remote" % akkaVersion,
          "com.typesafe.akka" % "akka-slf4j" % akkaVersion,
          "com.typesafe" % "config" % "1.0.0",
          "ch.qos.logback" % "logback-classic" % "1.0.3",
          "com.github.scala-incubator.io" %% "scala-io-core" % "0.4.0",
          "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.0",
          "org.apache.commons" % "commons-lang3" % "3.1"
        )
      ) ++ StartScriptPlugin.startScriptForClassesSettings
          ++ Seq(Keys.mainClass in Compile := Option("com.olegych.scastie.RendererMain"))
    )
  }

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    (StartScriptPlugin.startScriptForClassesSettings :+
        (Keys.mainClass in Compile := Option("play.core.server.NettyServer"))): _*
  ) dependsOn (renderer) aggregate (renderer)

}
