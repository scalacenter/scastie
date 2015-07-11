import com.typesafe.sbt.SbtStartScript
import com.typesafe.sbt.web.Import._
import sbt.Keys._
import sbt._

object ApplicationBuild extends Build {
  val scalaVersion = "2.11.7"
  val akkaVersion = "2.3.11"
  val jdkVersion = settingKey[String]("")

  def logging(allDependencies: Seq[ModuleID]): Seq[ModuleID] = {
    Seq(
      "org.slf4j" % "slf4j-api" % "1.7.6"
      , "org.slf4j" % "jul-to-slf4j" % "1.7.6"
      , "ch.qos.logback" % "logback-core" % "1.1.1" % Runtime
      , "ch.qos.logback" % "logback-classic" % "1.1.1" % Runtime
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
    , jdkVersion := "1.7"
    , scalacOptions += s"-target:jvm-${jdkVersion.value}"
    , javacOptions ++= Seq("-source", jdkVersion.value, "-target", jdkVersion.value)
    , updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)
    , Keys.scalaVersion := scalaVersion
    , Keys.libraryDependencies ++= Seq(
      "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1"
      , "org.scalaz" %% "scalaz-core" % "7.1.3"
    )
    , Keys.allDependencies ~= logging
  )
  def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % akkaVersion
  val renderer = project.settings(defaultSettings: _*).settings(SbtStartScript.startScriptForClassesSettings: _*).
    settings(
      Keys.mainClass in Compile := Option("com.olegych.scastie.RendererMain"),
      Keys.libraryDependencies ++= Seq(
        akka("actor"),
        akka("remote"),
        akka("slf4j"),
        "org.apache.commons" % "commons-lang3" % "3.1",
        "net.sourceforge.collections" % "collections-generic" % "4.01",
        "org.scala-lang" % "scala-compiler" % scalaVersion
      ))

  val scastie = project.in(file(".")).enablePlugins(com.typesafe.sbt.web.SbtWeb, play.PlayScala).settings(defaultSettings: _*).
    settings(SbtStartScript.startScriptForClassesSettings: _*).settings(
      Keys.allDependencies ~= (_.map(_.exclude("com.typesafe.play", "play-doc_2.11").exclude("com.typesafe.play", "play-docs_2.11")))
      , Keys.mainClass in Compile := Option("ProdNettyServer")
      , (WebKeys.public in Assets) := (classDirectory in Compile).value / "public"
      , (compile in Compile) <<= (compile in Compile).dependsOn(WebKeys.assets in Assets)
      , Keys.libraryDependencies ++=
    Seq("org.webjars" % "syntaxhighlighter" % "3.0.83", "org.webjars" %% "webjars-play" % "2.3.0", "org.webjars" % "bootstrap" % "3.1.0")
    ) dependsOn renderer aggregate renderer
}
