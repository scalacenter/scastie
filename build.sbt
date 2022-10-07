import scala.sys.process.ProcessLogger
import SbtShared._
import com.typesafe.sbt.SbtNativePackager.Universal

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.6.19"

val akkaHttpVersion = "10.2.9"

addCommandAlias("startAll", "sbtRunner/reStart;server/reStart;client/fastLinkJS")
addCommandAlias("startAllProd", "sbtRunner/reStart;server/fullLinkJS/reStart")

val fastLinkOutputDir = taskKey[String]("output directory for `yarn dev`")
val fullLinkOutputDir = taskKey[String]("output directory for `yarn build`")

val yarnBuild = taskKey[Unit]("builds es modules with `yarn build`")

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
      "io.sentry" % "sentry-logback" % "6.4.2"
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
    resolvers ++= Resolver.sonatypeOssRepos("public"),
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
    Compile / products += (client / baseDirectory).value / "dist",
    fullLinkJS / reStart := reStart.dependsOn(client / Compile / fullLinkJS / yarnBuild).evaluated,
    Universal / packageBin := (Universal / packageBin).dependsOn(client / Compile / fullLinkJS / yarnBuild).value,
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
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.7.0",
      "net.lingala.zip4j" % "zip4j" % "2.10.0",
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

import org.scalajs.linker.interface.ModuleSplitStyle

lazy val client = project
  .enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
  .settings(baseNoCrossSettings)
  .settings(baseJsSettings)
  .settings(
    externalNpm := {
      scala.sys.process.Process("yarn", baseDirectory.value.getParentFile)! ProcessLogger(line => ())
      baseDirectory.value.getParentFile
    },
    stFlavour := Flavour.ScalajsReact,
    scalaJSLinkerConfig := {
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toURI()
      scalaJSLinkerConfig.value.withModuleKind(ModuleKind.ESModule)
        .withRelativizeSourceMapBase(Some(dir))
    },
    fastLinkOutputDir := linkerOutputDirectory((Compile / fastLinkJS).value).getAbsolutePath(),
    fullLinkOutputDir := linkerOutputDirectory((Compile / fullLinkJS).value).getAbsolutePath(),
    yarnBuild := {
      scala.sys.process.Process("yarn build").!
    },
    test := {},
    Test / loadedTestFrameworks := Map(),
    stIgnore := List(
      "firacode", "font-awesome", "@sentry/browser", "@sentry/tracing",
      "react", "react-dom", "typeface-roboto-slab", "source-map-support"
      ),
    stEnableScalaJsDefined := Selection.AllExcept(),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % "2.1.1",
      "com.github.japgolly.scalajs-react" %%% "extra" % "2.1.1",
      "org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(api.js(ScalaVersions.js))

def linkerOutputDirectory(v: Attributed[org.scalajs.linker.interface.Report]): File = {
  v.get(scalaJSLinkerOutputDirectory.key).getOrElse {
    throw new MessageOnlyException(
        "Linking report was not attributed with output directory. " +
        "Please report this as a Scala.js bug.")
  }
}

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

