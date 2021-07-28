import SbtShared._
import com.typesafe.sbt.SbtNativePackager.Universal
import DockerHelper.{serverDockerfile, runnerDockerfile}

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % (
  if(module.startsWith("http")) "10.2.5" else "2.6.15"
)

addCommandAlias("startAll", "sbtRunner/reStart;server/reStart;client/fastOptJS/startWebpackDevServer")
addCommandAlias("startAllProd", "sbtRunner/reStart;server/fullOptJS/reStart")

lazy val scastie = project
  .in(file("."))
  .aggregate(
    (api.projectRefs ++ runtimeScala.projectRefs ++ List(
      balancer,
      client,
      instrumentation,
      sbtRunner,
      sbtScastie,
      server,
      storage,
      utils,
    ).map(_.project)):_*
  )
  .settings(baseSettings)
  .settings(
    cachedCiTestFull := {
      val _ = cachedCiTestFull.value
      val __ = (sbtRunner / docker / dockerfile).value
      val ___ = (server / Universal / packageBin).value
    },
  )
  .settings(Deployment.settings(server, sbtRunner))

lazy val testSettings =
  Seq(
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test
  )

lazy val loggingAndTest =
  Seq(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.getsentry.raven" % "raven-logback" % "8.0.3"
    )
  ) ++ testSettings

lazy val utils = project
  .in(file("utils"))
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    resolvers += Resolver.typesafeRepo("releases"),
    libraryDependencies ++= Seq(
      akka("serialization-jackson"),
      akka("protobuf"),
      akka("stream-typed"),
      akka("cluster-typed"),
      akka("slf4j"),
      akka("actor-testkit-typed") % Test
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm))

lazy val runnerRuntimeDependencies = (api.projectRefs ++ runtimeScala.projectRefs ++ Seq(
    sbtScastie.project
  )).map(_ / publishLocal)

lazy val runnerRuntimeDependenciesInTest = Seq(
  Test / test := (Test / test).dependsOn(runnerRuntimeDependencies: _*).value,
  Test / testOnly := (Test / testOnly).dependsOn(runnerRuntimeDependencies: _*).evaluated,
  Test / testQuick := (Test / testQuick).dependsOn(runnerRuntimeDependencies: _*).evaluated
)

lazy val smallRunnerRuntimeDependenciesInTest = {
  lazy val smallRunnerRuntimeDependencies = Seq(
    api.jvm(ScalaVersions.jvm),
    api.jvm(ScalaVersions.sbt),
    runtimeScala.jvm(ScalaVersions.jvm),
    runtimeScala.jvm(ScalaVersions.sbt),
    sbtScastie
  ).map(_ / publishLocal)
  Seq(
    Test / test := (Test / test).dependsOn(smallRunnerRuntimeDependencies: _*).value,
    Test / testOnly := (Test / testOnly).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated,
    Test / testQuick := (Test / testQuick).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated
  )
}

lazy val sbtRunner = project
  .in(file("sbt-runner"))
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(runnerRuntimeDependenciesInTest)
  .settings(
    reStart / javaOptions += "-Xmx256m",
    Test / parallelExecution := false,
    reStart := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    resolvers += Resolver.sonatypeRepo("public"),
    libraryDependencies ++= Seq(
      akka("actor-testkit-typed") % Test,
      "org.scalameta" %% "scalafmt-core" % "3.0.0-RC6"
    ),
    dockerImageName := "scastie-sbt-runner",
    docker / dockerfile := runnerDockerfile(sbtScastie).dependsOn(runnerRuntimeDependencies: _*).value,
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), instrumentation, utils)
  .enablePlugins(sbtdocker.DockerPlugin, JavaServerAppPackaging, BuildInfoPlugin)

lazy val server = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    watchSources ++= (client / watchSources).value,
    Compile / products += (client / Compile / npmUpdate / crossTarget).value / "out",
    reStart := reStart.dependsOn(client / Compile / fastOptJS / webpack).evaluated,
    fullOptJS / reStart := reStart.dependsOn(client / Compile / fullOptJS / webpack).evaluated,
    Universal / packageBin := (Universal / packageBin).dependsOn(client / Compile / fullOptJS / webpack).value,
    reStart / javaOptions += "-Xmx512m",
    maintainer := "scalacenter",
    libraryDependencies ++= Seq(
      akka("http"),
      "com.softwaremill.akka-http-session" %% "core" % "0.5.10",
      "ch.megard" %% "akka-http-cors" % "0.4.2",
      akka("actor-testkit-typed") % Test,
      akka("http-testkit") % Test
    ),
    dockerImageName := "scastie-server",
    docker / dockerfile := serverDockerfile().value,
  )
  .enablePlugins(sbtdocker.DockerPlugin, JavaServerAppPackaging)
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, balancer)

lazy val balancer = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(smallRunnerRuntimeDependenciesInTest)
  .settings(
    libraryDependencies += akka("actor-testkit-typed") % Test
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, storage, sbtRunner % Test)

lazy val storage = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "net.lingala.zip4j" % "zip4j" % "1.3.1",
      "org.reactivemongo" %% "reactivemongo" % "0.20.1",
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.20.1-play28",
      "org.reactivemongo" %% "reactivemongo-play-json-compat" % "0.20.1-play28",
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, instrumentation)

val webpackDir = Def.setting {
  (ThisProject / baseDirectory).value / "webpack"
}

val webpackDevConf = Def.setting {
  Some(webpackDir.value / "webpack-dev.config.js")
}

val webpackProdConf = Def.setting {
  Some(webpackDir.value / "webpack-prod.config.js")
}

lazy val client = project
  .settings(baseNoCrossSettings)
  .settings(baseJsSettings)
  .settings(
    webpack / version := "3.5.5",
    startWebpackDevServer / version := "2.7.1",
    fastOptJS / webpackConfigFile := webpackDevConf.value,
    fullOptJS / webpackConfigFile := webpackProdConf.value,
    webpackMonitoredDirectories += (Compile / resourceDirectory).value,
    webpackResources := webpackDir.value * "*.js",
    webpackMonitoredFiles / includeFilter := "*",
    useYarn := true,
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    fullOptJS / webpackBundlingMode := BundlingMode.Application,
    test := {},
    Test / loadedTestFrameworks := Map(),
    Compile / npmDependencies ++= Seq(
      "codemirror" -> "5.62.2",
      "firacode" -> "1.205.0",
      "font-awesome" -> "4.7.0",
      "raven-js" -> "3.11.0",
      "react" -> "16.7.0",
      "react-dom" -> "16.7.0",
      "typeface-roboto-slab" -> "0.0.35",
    ),
    Compile / npmDevDependencies ++= Seq(
      "compression-webpack-plugin" -> "1.0.0",
      "clean-webpack-plugin" -> "0.1.16",
      "css-loader" -> "0.28.5",
      "extract-text-webpack-plugin" -> "3.0.0",
      "file-loader" -> "0.11.2",
      "html-webpack-plugin" -> "2.30.1",
      "node-sass" -> "4.14.1",
      "resolve-url-loader" -> "2.1.0",
      "sass-loader" -> "6.0.6",
      "style-loader" -> "0.18.2",
      "uglifyjs-webpack-plugin" -> "1.0.0",
      "webpack-merge" -> "4.1.0",
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "extra" % "1.7.7",
    )
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(api.js(ScalaVersions.js))

lazy val instrumentation = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.3.24",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0" % Test
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils)

lazy val api = SbtShared.api
lazy val runtimeScala = SbtShared.`runtime-scala`

lazy val sbtScastie = project
  .in(file("sbt-scastie"))
  .settings(orgSettings)
  .settings(
    moduleName := "sbt-scastie",
    sbtPlugin := true
  )
  .settings(version := versionRuntime)
  .dependsOn(api.jvm(ScalaVersions.sbt))

