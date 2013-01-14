import sbt._
import com.typesafe.startscript.StartScriptPlugin

object ApplicationBuild extends Build {
  val appName = "scastie"
  val appVersion = "1.0"

  val scalaVersion = "2.10.0"
  val akkaVersion = "2.1.0"

  val appDependencies = Seq(
    // Add your project dependencies here,
  )
  val renderer = {
    def akka(module: String) = {
      "com.typesafe.akka" %% ("akka-" + module) % akkaVersion
    }
    def scalaIo(module: String) = {
      "com.github.scala-incubator.io" % ("scala-io-" + module + "_2.10.0-RC1") % "0.4.1"
    }
    Project(id = "renderer", base = file("renderer"),
      settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA") ++ Seq(
        Keys.scalaVersion := scalaVersion
        , Keys.libraryDependencies ++= Seq(
          "org.slf4j" % "jul-to-slf4j" % "1.6.6",
          akka("actor"),
          akka("remote"),
          akka("slf4j"),
          "com.typesafe" % "config" % "1.0.0",
          "ch.qos.logback" % "logback-classic" % "1.0.7",
          scalaIo("core"),
          scalaIo("file"),
          "org.apache.commons" % "commons-lang3" % "3.1",
          "net.sourceforge.collections" % "collections-generic" % "4.01",
          "org.scala-lang" % "scala-compiler" % scalaVersion
        )
      ) ++ StartScriptPlugin.startScriptForClassesSettings
          ++ Seq(Keys.mainClass in Compile := Option("com.olegych.scastie.RendererMain"))
    )
  }

  val main = play.Project(appName, appVersion, appDependencies).settings(
    (StartScriptPlugin.startScriptForClassesSettings ++
        Seq(Keys.mainClass in Compile := Option("play.core.server.NettyServer")
          , Keys.scalaVersion := scalaVersion
        )): _*
  ) dependsOn (renderer) aggregate (renderer)

}
