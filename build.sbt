import ScalaJSHelper._
import Deployment._
import org.scalajs.sbtplugin.JSModuleID
import org.scalajs.sbtplugin.cross.CrossProject

lazy val orgSettings = Seq(
  organization := "org.scastie",
  version := "0.3.0-SNAPSHOT"
)

lazy val baseSettings = Seq(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    scalaVersion := "2.11.8",
    scalacOptions := {
    val extraOptions =
      if (scalaBinaryVersion.value != "2.10") {
        Seq("-Ywarn-unused-import")
      } else Seq()
    Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    ) ++ extraOptions
  },
    console := (console in Test).value,
    scalacOptions in (Test, console) -= "-Ywarn-unused-import",
    scalacOptions in (Compile, consoleQuick) -= "-Ywarn-unused-import",
    allDependencies ~= logging
  ) ++ orgSettings

def logging(allDependencies: Seq[ModuleID]): Seq[ModuleID] = {
  Seq(
    "org.slf4j" % "slf4j-api" % "1.7.6",
    "org.slf4j" % "jul-to-slf4j" % "1.7.6",
    "ch.qos.logback" % "logback-core" % "1.1.1" % Runtime,
    "ch.qos.logback" % "logback-classic" % "1.1.1" % Runtime,
    "org.slf4j" % "jcl-over-slf4j" % "1.7.6" % Runtime,
    "org.slf4j" % "log4j-over-slf4j" % "1.7.6" % Runtime
  ) ++
    allDependencies.map(
      _.exclude("commons-logging", "commons-logging")
        .exclude("log4j", "log4j")
        .exclude("org.slf4j", "slf4j-log4j12")
        .exclude("org.slf4j", "slf4j-jcl")
        .exclude("org.slf4j", "slf4j-jdk14")
    )
}

lazy val utils = project
  .in(file("utils"))
  .settings(baseSettings)
  .settings(
    libraryDependencies += akka("actor")
  )
  .disablePlugins(play.PlayScala)
  .dependsOn(api211JVM)

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.3.11"

val upickleVersion = "0.4.4"

lazy val runnerRuntimeDependencies = Seq(
  runtimeScala210JVM,
  runtimeScala210JS,
  runtimeScala211JVM,
  runtimeScala211JS,
  runtimeScala212JVM,
  runtimeScala212JS,
  api210JVM,
  api210JS,
  api211JVM,
  api211JS,
  api212JVM,
  api212JS,
  runtimeDotty,
  sbtApi210,
  sbtApi211,
  sbtScastie
).map(publishLocal in _)

lazy val sbtRunnerRuntimeDependencies = Seq(
  publishLocal in sbtScastie,
  publishLocal in runtimeScala211JVM,
  publishLocal in api211JVM,
  publishLocal in sbtApi210,
  publishLocal in sbtApi211
)

lazy val sbtRunner = project
  .in(file("sbt-runner"))
  .settings(baseSettings)
  .settings(
    scalacOptions -= "-Xfatal-warnings",
    reStart := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    libraryDependencies ++= Seq(
      akka("actor"),
      akka("testkit") % Test,
      akka("remote"),
      akka("slf4j"),
      "com.geirsson" %% "scalafmt" % "0.5.1"
    ),
    imageNames in docker := Seq(
      ImageName(
        namespace = Some("scalacenter"),
        repository = "scastie-sbt-runner",
        tag = Some(version.value)
      )
    ),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _ *) => MergeStrategy.discard
      case in @ PathList("reference.conf", xs @ _ *) => {
        val old = (assemblyMergeStrategy in assembly).value
        old(in)
      }
      case x => MergeStrategy.first
    },
    dockerfile in docker := Def.task {
      // run crossPublishLocalRuntime
      val ivy = ivyPaths.value.ivyHome.get

      val org = organization.value
      val artifact = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("scalacenter/scastie-docker-sbt:0.13.13")

        add(ivy / "local" / org, s"/root/.ivy2/local/$org")

        add(artifact, artifactTargetPath)

        entryPoint("java", "-Xmx256M", "-Xms256M", "-jar", artifactTargetPath)
      }
    }.dependsOn(runnerRuntimeDependencies: _*).value,
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "com.olegych.scastie.buildinfo",
    test in Test := (test in Test)
      .dependsOn(sbtRunnerRuntimeDependencies: _*)
      .value,
    testOnly in Test := (testOnly in Test)
      .dependsOn(sbtRunnerRuntimeDependencies: _*)
      .evaluated,
    testQuick in Test := (testQuick in Test)
      .dependsOn(sbtRunnerRuntimeDependencies: _*)
      .evaluated
  )
  .dependsOn(sbtApi211, api211JVM, instrumentation, utils)
  .enablePlugins(DockerPlugin, BuildInfoPlugin)
  .disablePlugins(play.PlayScala)

lazy val server = project
  .in(file("scastie"))
  .settings(baseSettings)
  .settings(packageScalaJS(client))
  .settings(
    scalacOptions --= Seq(
      "-Ywarn-unused-import",
      "-Xfatal-warnings"
    ),
    allDependencies ~= (_.map(
      _.exclude("com.typesafe.play", "play-doc_2.11")
        .exclude("com.typesafe.play", "play-docs_2.11")
        .exclude("com.lihaoyi", "upickle_sjs0.6_2.11")
    )),
    libraryDependencies += akka("remote"),
    mainClass in Compile := Option("ProdNettyServer"),
    products in Compile := (products in Compile)
      .dependsOn(WebKeys.assets in Assets)
      .value,
    reStart := reStart.dependsOn(WebKeys.assets in Assets).evaluated,
    WebKeys.public in Assets := (classDirectory in Compile).value / "public",
    mappings in (Compile, packageBin) += (fullOptJS in (client, Compile)).value.data -> "public/client-opt.js"
  )
  .enablePlugins(SbtWeb, play.PlayScala)
  .dependsOn(client, api211JVM, utils)

lazy val scastie = project
  .in(file("."))
  .aggregate(
    server,
    instrumentation,
    sbtRunner,
    codemirror,
    client,
    runtimeDotty,
    sbtApi210,
    sbtApi211,
    sbtScastie,
    runtimeScala211JVM,
    runtimeScala211JS,
    api211JVM,
    api211JS
  )
  .settings(orgSettings)
  .settings(
    deploy := deployTask(server, sbtRunner).value,
    deployServer := deployServerTask(server, sbtRunner).value,
    deployQuick := deployQuickTask(server, sbtRunner).value,
    deployServerQuick := deployServerQuickTask(server, sbtRunner).value,
    run := {
      (reStart in sbtRunner).toTask("").value
      (reStart in server).toTask("").value
    }
  )

/* codemirror is a facade to the javascript rich editor codemirror*/
lazy val codemirror = project
  .settings(baseSettings)
  .settings(
    test := {},
    scalacOptions -= "-Ywarn-dead-code",
    jsDependencies ++= {
      def codemirrorD(path: String): JSModuleID =
        "org.webjars.bower" % "codemirror" % "5.18.2" % "compile" / s"$path.js" minified s"$path.js"

      List(
        "lib/codemirror",
        "addon/comment/comment",
        "addon/dialog/dialog",
        "addon/edit/closebrackets",
        "addon/edit/matchbrackets",
        "addon/fold/brace-fold",
        "addon/fold/foldcode",
        "addon/hint/show-hint",
        "addon/runmode/runmode",
        "addon/scroll/scrollpastend",
        "addon/scroll/simplescrollbars",
        "addon/search/match-highlighter",
        "addon/search/search",
        "addon/search/searchcursor",
        "keymap/sublime",
        "mode/clike/clike"
      ).map(codemirrorD)
    },
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.1"
  )
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(play.PlayScala)

/* frontend code */
def react(artifact: String, name: String): JSModuleID =
  "org.webjars.bower" % "react" % "15.3.2" % "compile" / s"$artifact.js" minified s"$artifact.min.js" commonJSName name

def react(artifact: String, name: String, depends: String): JSModuleID =
  react(artifact, name).dependsOn(s"$depends.js")

lazy val client = project
  .settings(baseSettings)
  .settings(
    JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,
    skip in packageJSDependencies := false,
    test := {},
    jsDependencies ++= Seq(
      react("react-with-addons", "React"),
      react("react-dom", "ReactDOM", "react-with-addons"),
      react("react-dom-server", "ReactDOMServer", "react-dom")
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra" % "0.11.2",
      "org.webjars.bower" % "open-iconic" % "1.1.1"
    )
  )
  .disablePlugins(play.PlayScala)
  .enablePlugins(ScalaJSPlugin, SbtWeb)
  .dependsOn(codemirror, api211JS)

/*  instrument a program to add a Map[Position, (Value, Type)]

class A {
  val a = 1 + 1 // << 2: Int
  3 + 3         // << 6: Int
}

 */

lazy val instrumentation = project
  .settings(baseSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "1.2.0",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0" % Test
    )
  )
  .disablePlugins(play.PlayScala)

def crossDir(projectId: String) = file(".cross/" + projectId)
def dash(name: String) = name.replaceAllLiterally(".", "-")

/* api is for the communication between sbt <=> server <=> frontend */
def api(scalaV: String) = {
  val projectName = "api"
  val projectId = s"$projectName-${dash(scalaV)}"
  CrossProject(id = projectId,
               base = crossDir(projectId),
               crossType = CrossType.Full)
    .settings(baseSettings)
    .settings(
      buildInfoKeys := Seq[BuildInfoKey](
        version,
        BuildInfoKey.action("githash") {
          import sys.process._
          if (!sys.env.contains("CI")) {
            Process("git describe --long --dirty --always").lines.mkString("")
          } else "CI"
        }
      ),
      buildInfoPackage := "com.olegych.scastie.buildinfo",
      scalaVersion := scalaV,
      moduleName := projectName,
      libraryDependencies ++= Seq(
        "com.lihaoyi" %%% "autowire" % "0.2.6",
        "com.lihaoyi" %%% "upickle" % upickleVersion
      ),
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "src" / "main" / "scala"
    )
    .jsSettings(
      test := {},
      libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.1"
    )
    .enablePlugins(BuildInfoPlugin)
    .disablePlugins(play.PlayScala)
}

val api210 = api("2.10.6")
val api211 = api("2.11.8")
val api212 = api("2.12.1")

lazy val api210JVM = api210.jvm
lazy val api210JS = api210.js
lazy val api211JVM = api211.jvm
lazy val api211JS = api211.js
lazy val api212JVM = api212.jvm
lazy val api212JS = api212.js

/* runtime* pretty print values and type */
def runtimeScala(scalaV: String, apiProject: CrossProject) = {
  val projectName = "runtime-scala"
  val projectId = s"$projectName-${dash(scalaV)}"
  CrossProject(id = projectId,
               base = crossDir(projectId),
               crossType = CrossType.Pure)
    .settings(baseSettings)
    .settings(
      scalaVersion := scalaV,
      moduleName := projectName,
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / projectName / "src" / "main" / "scala",
      libraryDependencies ++= Seq(
        "com.lihaoyi" %%% "upickle" % upickleVersion,
        "com.lihaoyi" %%% "pprint" % upickleVersion
      )
    )
    .jsSettings(test := {})
    .dependsOn(apiProject)
    .disablePlugins(play.PlayScala)
}

val runtimeScala210 = runtimeScala("2.10.6", api210)
val runtimeScala211 = runtimeScala("2.11.8", api211)
val runtimeScala212 = runtimeScala("2.12.1", api212)

lazy val runtimeScala210JVM = runtimeScala210.jvm
lazy val runtimeScala210JS = runtimeScala210.js
lazy val runtimeScala211JVM = runtimeScala211.jvm
lazy val runtimeScala211JS = runtimeScala211.js
lazy val runtimeScala212JVM = runtimeScala212.jvm
lazy val runtimeScala212JS = runtimeScala212.js

lazy val runtimeDotty = project
  .in(file("runtime-dotty"))
  .settings(orgSettings)
  .settings(
    moduleName := "runtime-dotty",
    scalaVersion := "0.1-20161203-9ceed92-NIGHTLY",
    scalaOrganization := "ch.epfl.lamp",
    scalaBinaryVersion := "2.11",
    scalaCompilerBridgeSource := ("ch.epfl.lamp" % "dotty-sbt-bridge" % "0.1.1-20161203-9ceed92-NIGHTLY" % "component")
      .sources(),
    autoScalaLibrary := false,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-library" % "2.11.5",
      "com.lihaoyi" %% "upickle" % upickleVersion
    )
  )
  .dependsOn(api211JVM)
  .disablePlugins(play.PlayScala)

/* sbtApi is for the communication between sbt and the sbt-runner */
def sbtApi(scalaV: String) = {
  val projectId = s"sbt-api-${dash(scalaV)}"
  Project(id = projectId, base = crossDir(projectId))
    .settings(orgSettings)
    .settings(
      unmanagedSourceDirectories in Compile += (baseDirectory in ThisBuild).value / "sbt-api" / "src" / "main",
      scalaVersion := scalaV,
      libraryDependencies += "com.lihaoyi" %% "upickle" % upickleVersion
    )
    .disablePlugins(play.PlayScala)
}

lazy val sbtApi210 = sbtApi("2.10.6")
lazy val sbtApi211 = sbtApi("2.11.8")

lazy val sbtScastie = project
  .in(file("sbt-scastie"))
  .settings(orgSettings)
  .settings(
    moduleName := "sbt-scastie",
    scalaVersion := "2.10.6",
    sbtPlugin := true
  )
  .dependsOn(sbtApi210)
