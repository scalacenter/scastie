import ScalaJSHelper._
import Deployment._

import org.scalajs.sbtplugin.JSModuleID
import org.scalajs.sbtplugin.cross.CrossProject
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport.{jsEnv, scalaJSStage}
import sbt.Keys._
import scala.util.Try

val latest210 = "2.10.6"
val latest211 = "2.11.11"
val latest212 = "2.12.3"
val latest213 = "2.13.0-M1"

val runtimeProjectName = "runtime-scala"

val currentScalaVersion = latest212

lazy val orgSettings = Seq(
  organization := "org.scastie",
  version := {
    val base = "0.26.0"
    if (gitIsDirty())
      base + "-SNAPSHOT"
    else {
      val hash = gitHash()
      s"$base+$hash"
    }
  }
)

val playJsonVersion = "2.6.2"
val scalajsDomVersion = "0.9.3"
val scalaTestVersion = "3.0.1"
val akkaHttpVersion = "10.0.6"

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.5.2"

def akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
def akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

val playJson =
  libraryDependencies += "com.typesafe.play" %%% "play-json" % playJsonVersion

lazy val scastie = project
  .in(file("."))
  .aggregate(
    api210JS,
    api210JVM,
    api211JS,
    api211JVM,
    apiJS,
    apiJVM,
    api213JS,
    api213JVM,
    balancer,
    client,
    codemirror,
    instrumentation,
    runtimeScala210JS,
    runtimeScala210JVM,
    runtimeScala211JS,
    runtimeScala211JVM,
    runtimeScalaJS,
    runtimeScalaJVM,
    runtimeScala213JS,
    runtimeScala213JVM,
    sbtRunner,
    sbtScastie,
    server,
    storage,
    utils
  )
  .settings(baseSettings)
  .settings(Deployment.settings(server, sbtRunner))
  .settings(addCommandAlias("drone", ";test ;server/universal:packageBin"))

lazy val baseSettings = Seq(
  scalaVersion := currentScalaVersion,
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ),
  console := (console in Test).value,
  scalacOptions in (Test, console) -= "-Ywarn-unused-import",
  scalacOptions in (Compile, consoleQuick) -= "-Ywarn-unused-import"
) ++ orgSettings

lazy val loggingAndTest =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "com.getsentry.raven" % "raven-logback" % "8.0.3",
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  )

lazy val remapSourceMap =
  scalacOptions ++= {
    val ver = version.value
    val fromScastie = (baseDirectory in LocalRootProject).value.toURI.toString
    val toScastie =
      s"https://raw.githubusercontent.com/scalacenter/scastie/${gitHash()}"

    Map(fromScastie -> toScastie).map {
      case (from, to) =>
        s"-P:scalajs:mapSourceURI:$from->$to"
    }.toList
  }

lazy val utils = project
  .in(file("utils"))
  .settings(baseSettings)
  .settings(
    libraryDependencies += akka("stream")
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

lazy val sbtRunner = project
  .in(file("sbt-runner"))
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(
    javaOptions in reStart += "-Xmx256m",
    parallelExecution in Test := false,
    scalacOptions -= "-Xfatal-warnings", // Thread.stop
    reStart := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    resolvers += Resolver.sonatypeRepo("public"),
    libraryDependencies ++= Seq(
      akka("actor"),
      akka("testkit") % Test,
      akka("remote"),
      akka("slf4j"),
      akkaHttp,
      "com.geirsson" %% "scalafmt-core" % "1.1.0",
      // sbt-ensime 1.12.14 creates .ensime with 2.0.0-M4 server jar
      "org.ensime" %% "jerky" % "2.0.0-M4",
      "org.ensime" %% "s-express" % "2.0.0-M4"
    ),
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("scalacenter"),
        repository = "scastie-sbt-runner",
        tag = Some(gitHash())
      )
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case in @ PathList("reference.conf", xs @ _*) => {
        val old = (assemblyMergeStrategy in assembly).value
        old(in)
      }
      case x => MergeStrategy.first
    },
    test in assembly := {},
    dockerfile in docker := Def
      .task {
        val base = (baseDirectory in ThisBuild).value
        val ivy = ivyPaths.value.ivyHome.get

        val org = organization.value
        val artifact = assembly.value
        val artifactTargetPath = s"/app/${artifact.name}"

        val logbackConfDestination = "/home/scastie/logback.xml"

        new Dockerfile {
          from("scalacenter/scastie-docker-sbt:0.0.42")

          add(ivy / "local" / org, s"/home/scastie/.ivy2/local/$org")

          add(artifact, artifactTargetPath)

          add(base / "deployment" / "logback.xml", logbackConfDestination)

          entryPoint("java",
                     "-Xmx256M",
                     "-Xms256M",
                     s"-Dlogback.configurationFile=$logbackConfDestination",
                     "-jar",
                     artifactTargetPath)
        }
      }
      .dependsOn(runnerRuntimeDependencies: _*)
      .value,
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
  .dependsOn(apiJVM, instrumentation, utils)
  .enablePlugins(sbtdocker.DockerPlugin, BuildInfoPlugin)

lazy val server = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(packageScalaJS(client))
  .settings(
    javaOptions in reStart += "-Xmx512m",
    reStart := reStart.dependsOn(WebKeys.assets in (client, Assets)).evaluated,
    (packageBin in Universal) := (packageBin in Universal)
      .dependsOn(WebKeys.assets in (client, Assets))
      .value,
    unmanagedResourceDirectories in Compile += (WebKeys.public in (client, Assets)).value,
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
  .enablePlugins(SbtWeb, JavaServerAppPackaging)
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

/* codemirror is a facade to the javascript rich editor codemirror*/
lazy val codemirror = project
  .settings(baseSettings)
  .settings(
    test := {},
    scalacOptions -= "-Ywarn-dead-code",
    jsDependencies ++= {
      // latest: "5.26.0"
      def codemirrorD(path: String): JSModuleID =
        "org.webjars.bower" % "codemirror" % "5.18.2" % "compile" / s"$path.js" minified s"$path.js"

      List(
        "addon/comment/comment",
        "addon/dialog/dialog",
        "addon/edit/closebrackets",
        "addon/edit/matchbrackets",
        "addon/fold/brace-fold",
        "addon/fold/foldcode",
        "addon/hint/show-hint",
        "addon/runmode/runmode",
        "addon/scroll/simplescrollbars",
        "addon/search/match-highlighter",
        "addon/search/search",
        "addon/search/searchcursor",
        "addon/wrap/hardwrap",
        "keymap/sublime",
        "lib/codemirror",
        "mode/clike/clike"
      ).map(codemirrorD)
    },
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
      "org.querki" %%% "querki-jsext" % "0.8"
    ),
    jsEnv in Test := new PhantomJS2Env(scalaJSPhantomJSClassLoader.value)
  )
  .enablePlugins(ScalaJSPlugin)

def react(artifact: String,
          name: String,
          configuration: Configuration = Compile): JSModuleID =
  "org.webjars.bower" % "react" % "15.5.4" % configuration / s"$artifact.js" minified s"$artifact.min.js" commonJSName name

def reactWithDepends(artifact: String,
                     name: String,
                     depends: String,
                     configuration: Configuration = Compile): JSModuleID =
  react(artifact, name, configuration).dependsOn(s"$depends.js")

lazy val client = project
  .settings(baseSettings)
  .settings(
    skip in packageJSDependencies := false,
    jsDependencies ++= Seq(
      react("react-with-addons", "React"),
      reactWithDepends("react-dom", "ReactDOM", "react-with-addons"),
      reactWithDepends("react-dom-server", "ReactDOMServer", "react-dom"),
      reactWithDepends("react-dom", "ReactDOM", "react-with-addons", Test),
      reactWithDepends("react-dom-server", "ReactDOMServer", "react-dom", Test),
      RuntimeDOM % Test,
      "org.webjars.bower" % "raven-js" % "3.11.0" /
        "dist/raven.js" minified "dist/raven.min.js"
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra" % "1.0.0",
      "com.github.japgolly.scalajs-react" %%% "test" % "1.0.0" % Test,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % Test,
      "org.webjars" % "font-awesome" % "4.7.0",
      "org.webjars.npm" % "firacode" % "1.205.0",
      "org.webjars.bower" % "bourbon" % "3.1.8",
      "org.webjars.bower" % "neat" % "1.8.0"
    ),
    requiresDOM := true,
    scalaJSStage in Test := FastOptStage,
    jsEnv in Test := new PhantomJS2Env(scalaJSPhantomJSClassLoader.value)
  )
  .enablePlugins(ScalaJSPlugin, SbtWeb)
  .dependsOn(codemirror, apiJS)

lazy val instrumentation = project
  .settings(baseSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      // see https://github.com/ensime/ensime-server/issues/1784
      // not 1.8.0 because it depends on fastparse 0.4.4 and this breaks ensime (using fastparse 0.4.2)
      "org.scalameta" %% "scalameta" % "1.8.0",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0" % Test
    )
  )
  .dependsOn(apiJVM, utils)

def crossDir(projectId: String) = file(".cross/" + projectId)
def dash(name: String) = name.replaceAllLiterally(".", "-")

/* api is for the communication between sbt <=> server <=> frontend */
def api(scalaV: String) = {
  val projectName = "api"
  val projectId =
    if (scalaV != currentScalaVersion) {
      s"$projectName-${dash(scalaV)}"
    } else projectName

  CrossProject(id = projectId,
               base = crossDir(projectId),
               crossType = CrossType.Pure)
    .settings(baseSettings)
    .settings(
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        version,
        "runtimeProjectName" -> runtimeProjectName,
        BuildInfoKey.action("gitHash") { gitHash() }
      ),
      buildInfoPackage := "com.olegych.scastie.buildinfo",
      scalaVersion := scalaV,
      moduleName := projectName,
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "src" / "main" / "scala"
    )
    .settings(playJson)
    .jsSettings(
      test := {},
      libraryDependencies += "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion
    )
    .jsSettings(remapSourceMap)
    .enablePlugins(BuildInfoPlugin)
}

val api210 = api(latest210)
val api211 = api(latest211)
val apiCurrent = api(currentScalaVersion)
val api213 = api(latest213)

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

val runtimeScala210 = runtimeScala(latest210, api210)
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
    scalaVersion := latest210,
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
