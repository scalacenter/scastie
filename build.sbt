import SbtShared._
import com.typesafe.sbt.SbtNativePackager.Universal

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.6.19"

val akkaHttpVersion = "10.2.9"

addCommandAlias("startAll", "sbtRunner/reStart;server/reStart;client/fastOptJS/startWebpackDevServer")
addCommandAlias("startAllProd", "sbtRunner/reStart;server/fullOptJS/reStart")

ThisBuild / packageTimestamp := None

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
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.12" % Test
  )

lazy val loggingAndTest =
  Seq(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
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
      akka("protobuf"),
      akka("stream"),
      akka("actor"),
      akka("remote"),
      akka("slf4j"),
      akka("testkit") % Test
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm))

lazy val runnerRuntimeDependencies = (api.projectRefs ++ runtimeScala.projectRefs ++ Seq(
    sbtScastie.project
  )).map(_ / publishLocal)

lazy val runnerRuntimeDependenciesInTest = Seq(
  assembly / test := {},
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
    assembly / test := {},
    Test / test := (Test / test).dependsOn(smallRunnerRuntimeDependencies: _*).value,
    Test / testOnly := (Test / testOnly).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated,
    Test / testQuick := (Test / testQuick).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated
  )
}

lazy val dockerOrg = "scalacenter"

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
      akka("actor"),
      akka("testkit") % Test,
      akka("cluster"),
      akka("slf4j"),
      "org.scalameta" %% "scalafmt-core" % "3.5.8"
    ),
    docker / imageNames := Seq(
      ImageName(
        namespace = Some(dockerOrg),
        repository = "scastie-sbt-runner",
        tag = Some(gitHashNow)
      )
    ),
    docker / buildOptions := (docker / buildOptions).value.copy(additionalArguments = List("--add-host", "jenkins.scala-sbt.org:127.0.0.1")),
    docker / dockerfile := Def
      .task {
        DockerHelper(
          baseDirectory = (ThisBuild / baseDirectory).value.toPath,
          sbtTargetDir = target.value.toPath,
          ivyHome = ivyPaths.value.ivyHome.get.toPath,
          organization = organization.value,
          artifact = assembly.value.toPath,
          sbtScastie = (sbtScastie / moduleName).value,
          sbtVersion = sbtVersion.value,
        )
      }
      .dependsOn(runnerRuntimeDependencies: _*)
      .value,
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case in @ PathList("reference.conf", xs @ _*) => {
        val old = (assembly / assemblyMergeStrategy).value
        old(in)
      }
      case x => MergeStrategy.first
    }
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), instrumentation, utils)
  .enablePlugins(sbtdocker.DockerPlugin, BuildInfoPlugin)

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
      "org.apache.commons" % "commons-text" % "1.9",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.softwaremill.akka-http-session" %% "core" % "0.7.0",
      "ch.megard" %% "akka-http-cors" % "1.1.3",
      akka("cluster"),
      akka("slf4j"),
      akka("testkit") % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
    )
  )
  .enablePlugins(JavaServerAppPackaging)
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, balancer)

lazy val balancer = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(smallRunnerRuntimeDependenciesInTest)
  .settings(
    libraryDependencies += akka("testkit") % Test
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, storage, sbtRunner % Test)

lazy val storage = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "net.lingala.zip4j" % "zip4j" % "2.10.0",
      "org.reactivemongo" %% "reactivemongo" % "1.0.10",
      "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.0.10-play29",
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
      "codemirror" -> "5.50.0",
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
      "com.github.japgolly.scalajs-react" %%% "core" % "2.1.1",
      "com.github.japgolly.scalajs-react" %%% "extra" % "2.1.1",
    )
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(api.js(ScalaVersions.js))

lazy val instrumentation = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.5.5",
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

