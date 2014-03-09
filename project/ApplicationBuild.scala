import sbt._
import com.typesafe.sbt.SbtStartScript

object ApplicationBuild extends Build {
  val appName = "scastie"
  val appVersion = "1.0"

  val scalaVersion = "2.10.3"
  val akkaVersion = "2.2.4"

  def logging(allDependencies: Seq[ModuleID]): Seq[ModuleID] = {
    Seq(
      "org.slf4j" % "slf4j-api" % "1.7.6"
      , "org.slf4j" % "jul-to-slf4j" % "1.7.6"
      , "ch.qos.logback" % "logback-core" % "1.0.13" % Runtime
      , "ch.qos.logback" % "logback-classic" % "1.0.13" % Runtime
      , "org.slf4j" % "jcl-over-slf4j" % "1.7.6" % Runtime
      , "org.slf4j" % "log4j-over-slf4j" % "1.7.6" % Runtime
    ) ++
      allDependencies.map(
        _.exclude("commons-logging", "commons-logging")
          .exclude("log4j", "log4j")
          .exclude("org.slf4j", "slf4j-log4j12")
          .exclude("org.slf4j", "slf4j-jcl")
          .exclude("org.slf4j", "slf4j-jdk14")
      )
  }

  val defaultSettings = Seq(
    Keys.incOptions := Keys.incOptions.value.withNameHashing(true)
    , Keys.scalaVersion := scalaVersion
    , Keys.libraryDependencies ++= Seq(
      "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2"
      , "org.scalaz" %% "scalaz-core" % "7.0.6"
    )
    , Keys.allDependencies ~= logging
  )
  val renderer = {
    def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % akkaVersion
    Project(id = "renderer", base = file("renderer"),
      settings = Defaults.defaultSettings ++ defaultSettings ++
        play.Project.intellijCommandSettings ++
        Seq(
          Keys.libraryDependencies ++= Seq(
            akka("actor"),
            akka("remote"),
            akka("slf4j"),
            "org.apache.commons" % "commons-lang3" % "3.1",
            "net.sourceforge.collections" % "collections-generic" % "4.01",
            "org.scala-lang" % "scala-compiler" % scalaVersion
          ))
        ++ SbtStartScript.startScriptForClassesSettings
        ++ Seq(Keys.mainClass in Compile := Option("com.olegych.scastie.RendererMain"))
    )
  }

  val main = play.Project(appName, appVersion).settings(defaultSettings:_*).settings(
    SbtStartScript.startScriptForClassesSettings ++
      Seq(Keys.mainClass in Compile := Option("ProdNettyServer")
        , Keys.watchSources <++= Keys.baseDirectory map { path => ((path / "public") ** "*").get}
        , Keys.libraryDependencies ++= Seq(
          "org.webjars" % "syntaxhighlighter" % "3.0.83"
          , "org.webjars" %% "webjars-play" % "2.2.1-2"
          , "org.webjars" % "bootstrap" % "3.1.0")
      ): _*
  ) dependsOn renderer aggregate renderer

}
