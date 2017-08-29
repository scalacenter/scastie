import ScalaJSHelper._
import Deployment._
import SbtShared._

import org.scalajs.sbtplugin.JSModuleID
import org.scalajs.sbtplugin.cross.CrossProject
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.{jsEnv, scalaJSStage}

import scala.util.Try
import java.io.FileNotFoundException

import scalajsbundler.util.JSON

import scala.util.Try

val scalaTestVersion = "3.0.1"
val akkaHttpVersion = "10.0.9"

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.5.2"

def akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
def akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

addCommandAlias(
  "startAll",
  List(
    "sbtRunner/reStart",
    "ensimeRunner/reStart",
    "server/reStart",
    "client/fastOptJS::startWebpackDevServer",
    "client/fastOptJS"
  ).mkString(";", ";", "")
)

lazy val scastie = project
  .in(file("."))
  .aggregate(
    api210JS,
    api210JVM,
    api211JS,
    api211JVM,
    api213JS,
    api213JVM,
    apiJS,
    apiJVM,
    balancer,
    client,
    ensimeRunner,
    instrumentation,
    runtimeScala210JS,
    runtimeScala210JVM,
    runtimeScala211JS,
    runtimeScala211JVM,
    runtimeScala213JS,
    runtimeScala213JVM,
    runtimeScalaJS,
    runtimeScalaJVM,
    sbtRunner,
    sbtScastie,
    server,
    storage,
    utils
  )
  .settings(baseSettings)
  .settings(Deployment.settings(server, sbtRunner, ensimeRunner))

lazy val loggingAndTest =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "com.getsentry.raven" % "raven-logback" % "8.0.3",
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  )

lazy val utils = project
  .in(file("utils"))
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      akka("stream"),
      akka("actor"),
      akka("remote"),
      akka("slf4j")
    )
  )
  .dependsOn(apiJVM)

lazy val runnerRuntimeDependencies = Seq(
  api210JS,
  api210JVM,
  api211JS,
  api211JVM,
  apiJS,
  apiJVM,
  api213JS,
  api213JVM,
  runtimeScala210JS,
  runtimeScala210JVM,
  runtimeScala211JS,
  runtimeScala211JVM,
  runtimeScalaJS,
  runtimeScalaJVM,
  runtimeScala213JS,
  runtimeScala213JVM,
  sbtScastie
).map(publishLocal in _)

lazy val runnerRuntimeDependenciesInTest = Seq(
  test in assembly := {},
  test in Test := (test in Test)
    .dependsOn(runnerRuntimeDependencies: _*)
    .value,
  testOnly in Test := (testOnly in Test)
    .dependsOn(runnerRuntimeDependencies: _*)
    .evaluated,
  testQuick in Test := (testQuick in Test)
    .dependsOn(runnerRuntimeDependencies: _*)
    .evaluated
)

lazy val dockerOrg = "scalacenter"

lazy val ensimeRunner = project
  .in(file("ensime-runner"))
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(runnerRuntimeDependenciesInTest)
  .settings(
    reStart := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    resolvers += Resolver.sonatypeRepo("public"),
    libraryDependencies ++= Seq(
      akka("actor"),
      akka("remote"),
      akka("testkit") % Test,
      akka("slf4j"),
      akkaHttp,
      "org.ensime" %% "jerky" % latestEnsime,
      "org.ensime" %% "s-express" % latestEnsime
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case in @ PathList("reference.conf", xs @ _*) =>
        val old = (assemblyMergeStrategy in assembly).value
        old(in)
      case x => MergeStrategy.first
    },
    imageNames in docker := Seq(
      ImageName(
        namespace = Some(dockerOrg),
        repository = "scastie-ensime-runner",
        tag = Some(gitHash())
      )
    ),
    dockerfile in docker := Def
      .task {
        DockerHelper(
          baseDirectory = (baseDirectory in ThisBuild).value.toPath,
          sbtTargetDir = target.value.toPath,
          ivyHome = ivyPaths.value.ivyHome.get.toPath,
          organization = organization.value,
          artifact = assembly.value.toPath,
          sbtScastie = (moduleName in sbtScastie).value
        )
      }
      .dependsOn(runnerRuntimeDependencies: _*)
      .value
  )
  .dependsOn(apiJVM, utils, sbtRunner)
  .enablePlugins(sbtdocker.DockerPlugin)

lazy val sbtRunner = project
  .in(file("sbt-runner"))
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(runnerRuntimeDependenciesInTest)
  .settings(
    javaOptions in reStart += "-Xmx256m",
    parallelExecution in Test := false,
    reStart := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    resolvers += Resolver.sonatypeRepo("public"),
    libraryDependencies ++= Seq(
      akka("actor"),
      akka("testkit") % Test,
      akka("remote"),
      akka("slf4j"),
      akkaHttp,
      "com.geirsson" %% "scalafmt-core" % "1.2.0"
    ),
    imageNames in docker := Seq(
      ImageName(
        namespace = Some(dockerOrg),
        repository = "scastie-sbt-runner",
        tag = Some(gitHashNow)
      )
    ),
    dockerfile in docker := Def
      .task {
        DockerHelper(
          baseDirectory = (baseDirectory in ThisBuild).value.toPath,
          sbtTargetDir = target.value.toPath,
          ivyHome = ivyPaths.value.ivyHome.get.toPath,
          organization = organization.value,
          artifact = assembly.value.toPath,
          sbtScastie = (moduleName in sbtScastie).value
        )
      }
      .dependsOn(runnerRuntimeDependencies: _*)
      .value,
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case in @ PathList("reference.conf", xs @ _*) => {
        val old = (assemblyMergeStrategy in assembly).value
        old(in)
      }
      case x => MergeStrategy.first
    }
  )
  .dependsOn(apiJVM, instrumentation, utils)
  .enablePlugins(sbtdocker.DockerPlugin, BuildInfoPlugin)

lazy val server = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(packageScalaJS(client))
  .settings(
    javaOptions in reStart += "-Xmx512m",
    libraryDependencies ++= Seq(
      "org.json4s" %% "json4s-native" % "3.5.2",
      "ch.megard" %% "akka-http-cors" % "0.2.1",
      "com.softwaremill.akka-http-session" %% "core" % "0.4.0",
      "de.heikoseeberger" %% "akka-sse" % "3.0.0",
      akkaHttp,
      akka("remote"),
      akka("slf4j")
    )
  )
  .enablePlugins(JavaServerAppPackaging)
  .dependsOn(apiJVM, utils, balancer)

lazy val balancer = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .dependsOn(apiJVM, utils, storage)

lazy val storage = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      akka("remote"),
      akkaHttpCore,
      "net.lingala.zip4j" % "zip4j" % "1.3.1"
    )
  )
  .dependsOn(apiJVM, utils, instrumentation)

lazy val client = project
  .settings(baseSettings)
  .settings(
    test := {},
    version in webpack := "3.5.5",
    version in installWebpackDevServer := "2.7.1",
    webpackConfigFile in startWebpackDevServer :=
      Some(baseDirectory.value / "webpack-dev.config.js"),
    webpackConfigFile in webpackReload :=
      Some(baseDirectory.value / "webpack-dev.config.js"),
    webpackConfigFile in fastOptJS :=
      Some(baseDirectory.value / "webpack-dev.config.js"),
    webpackConfigFile in fullOptJS :=
      Some(baseDirectory.value / "webpack-prod.config.js"),
    webpackMonitoredDirectories += (resourceDirectory in Compile).value,
    includeFilter in webpackMonitoredFiles := "*",
    useYarn := true,
    enableReloadWorkflow := true,
    emitSourceMaps := false,
    npmDependencies in Compile ++= Seq(
      "bourbon" -> "4.3.4",
      "bourbon-neat" -> "1.9.0",
      "codemirror" -> "5.28.0",
      "firacode" -> "1.205.0",
      "font-awesome" -> "4.7.0",
      "raven-js" -> "3.11.0",
      "react" -> "15.6.1",
      "react-dom" -> "15.6.1",
      "typeface-roboto-slab" -> "0.0.35"
    ),
    npmDevDependencies in Compile ++= Seq(
      "clean-webpack-plugin" -> "0.1.16",
      "css-loader" -> "0.28.5",
      "extract-text-webpack-plugin" -> "3.0.0",
      "file-loader" -> "0.11.2",
      "html-webpack-plugin" -> "2.30.1",
      "node-sass" -> "4.5.3",
      "resolve-url-loader" -> "2.1.0",
      "sass-loader" -> "6.0.6",
      "style-loader" -> "0.18.2",
      "webpack-merge" -> "4.1.0"
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra" % "1.1.0",
      "org.querki" %%% "querki-jsext" % "0.8"
    )
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(apiJS)

lazy val instrumentation = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "1.8.0",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0" % Test
    )
  )
  .dependsOn(apiJVM, utils)

val api210 = apiProject(sbt210)
val api211 = apiProject(latest211)
val apiCurrent = apiProject(currentScalaVersion)
val api213 = apiProject(latest213)

lazy val api210JS = api210.js
lazy val api210JVM = api210.jvm
lazy val api211JS = api211.js
lazy val api211JVM = api211.jvm
lazy val apiJS = apiCurrent.js
lazy val apiJVM = apiCurrent.jvm
lazy val api213JS = api213.js
lazy val api213JVM = api213.jvm

/* runtime* pretty print values and type */
def runtimeScala(scalaV: String, apiProject: CrossProject) = {
  val projectName = runtimeProjectName

  val projectId =
    if (scalaV != currentScalaVersion) {
      s"$projectName-${dash(scalaV)}"
    } else projectName

  CrossProject(id = projectId,
               base = crossDir(projectId),
               crossType = CrossType.Full)
    .settings(baseSettings)
    .settings(
      scalaVersion := scalaV,
      moduleName := projectName,
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "shared" / "src" / "main" / "scala"
    )
    .jsSettings(remapSourceMap)
    .jsSettings(
      test := {},
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "js" / "src" / "main" / "scala"
    )
    .jvmSettings(
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "jvm" / "src" / "main" / "scala"
    )
    .dependsOn(apiProject)
}

val runtimeScala210 = runtimeScala(sbt210, api210)
val runtimeScala211 = runtimeScala(latest211, api211)
val runtimeScalaCurrent = runtimeScala(currentScalaVersion, apiCurrent)
val runtimeScala213 = runtimeScala(latest213, api213)

lazy val runtimeScala210JS = runtimeScala210.js
lazy val runtimeScala210JVM = runtimeScala210.jvm
lazy val runtimeScala211JS = runtimeScala211.js
lazy val runtimeScala211JVM = runtimeScala211.jvm
lazy val runtimeScalaJS = runtimeScalaCurrent.js
lazy val runtimeScalaJVM = runtimeScalaCurrent.jvm
lazy val runtimeScala213JS = runtimeScala213.js
lazy val runtimeScala213JVM = runtimeScala213.jvm

lazy val sbtScastie = project
  .in(file("sbt-scastie"))
  .settings(orgSettings)
  .settings(
    moduleName := "sbt-scastie",
    scalaVersion := sbt210,
    sbtPlugin := true
  )
  .dependsOn(api210JVM)

// migration from upickle to play-json
// sbt migration/assembly
// scp ./migration/target/scala-2.12/migration-assembly-0.25.0-SNAPSHOT.jar scastie@scastie.scala-lang.org:migration.jar
// java -jar migration.jar snippets
lazy val migration = project
  .settings(baseSettings)
  .settings(
    moduleName := "sbt-scastie",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "0.4.4",
      "com.typesafe.play" %% "play-json" % "2.6.2"
    )
  )
