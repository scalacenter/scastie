import scala.sys.process.ProcessLogger

import com.typesafe.sbt.SbtNativePackager.Universal
import org.scalajs.linker.interface.ModuleSplitStyle
import SbtShared._

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.6.19"

val akkaHttpVersion = "10.2.9"

addCommandAlias("startAll", "sbtRunner/reStart;server/reStart;metalsRunner/reStart;client/fastLinkJS")
addCommandAlias("startAllProd", "sbtRunner/reStart;metalsRunner/reStart;server/fullLinkJS/reStart")

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
      metalsRunner
    ).map(_.project)): _*
  )
  .settings(baseSettings)
  .settings(
    cachedCiTestFull := {
      val _   = cachedCiTestFull.value
      val __  = (sbtRunner / docker / dockerfile).value
      val ___ = (server / Universal / packageBin).value
    }
  )
  .settings(Deployment.settings(server, sbtRunner, metalsRunner))

lazy val testSettings = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
)

lazy val loggingAndTest = Seq(
  libraryDependencies ++= Seq(
    "ch.qos.logback"              % "logback-classic" % "1.4.5",
    "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",
    "io.sentry"                   % "sentry-logback"  % "6.11.0"
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
  assembly / test  := {},
  Test / test      := (Test / test).dependsOn(runnerRuntimeDependencies: _*).value,
  Test / testOnly  := (Test / testOnly).dependsOn(runnerRuntimeDependencies: _*).evaluated,
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
    assembly / test  := {},
    Test / test      := (Test / test).dependsOn(smallRunnerRuntimeDependencies: _*).value,
    Test / testOnly  := (Test / testOnly).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated,
    Test / testQuick := (Test / testQuick).dependsOn(smallRunnerRuntimeDependencies: _*).evaluated
  )
}

lazy val dockerOrg = "scalacenter"

lazy val metalsRunner = project
  .in(file("metals-runner"))
  .settings(baseNoCrossSettings)
  .settings(
    fork := true, // we need to fork it, as it is dynamically loading classes, and they persist in the sbt shell instance
    docker / imageNames := Seq(
      ImageName(namespace = Some(dockerOrg), repository = "scastie-metals-runner", tag = Some(gitHashNow)),
      ImageName(namespace = Some(dockerOrg), repository = "scastie-metals-runner", tag = Some("latest"))
    ),
    executableScriptName := "server",
    docker / dockerfile := Def
      .task {
        DockerHelper.javaProject(
          baseDirectory = (ThisBuild / baseDirectory).value.toPath,
          organization = organization.value,
          artifactZip = (Universal / packageBin).value.toPath,
          configPath = (baseDirectory.value / "src" / "main" / "resources" / "application.conf").toPath
        )
      }
      .dependsOn(runnerRuntimeDependencies: _*)
      .value,
    maintainer   := "scalacenter",
    scalaVersion := ScalaVersions.stable3,
    libraryDependencies ++= Seq(
      "org.scalameta"        % "metals"              % "0.11.9" cross (CrossVersion.for3Use2_13),
      "org.eclipse.lsp4j"    % "org.eclipse.lsp4j"   % "0.19.0",
      "org.http4s"          %% "http4s-ember-server" % "0.23.17",
      "org.http4s"          %% "http4s-ember-client" % "0.23.17",
      "org.http4s"          %% "http4s-dsl"          % "0.23.17",
      "org.http4s"          %% "http4s-circe"        % "0.23.17",
      "io.circe"            %% "circe-generic"       % "0.14.3",
      "org.scalameta"       %% "munit"               % "0.7.29" % Test,
      "com.evolutiongaming" %% "scache"              % "4.2.3",
      "org.typelevel"       %% "munit-cats-effect-3" % "1.0.7"  % Test
    )
  )
  .enablePlugins(JavaServerAppPackaging, sbtdocker.DockerPlugin)
  .dependsOn(api.jvm(ScalaVersions.old3))

lazy val sbtRunner = project
  .in(file("sbt-runner"))
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(runnerRuntimeDependenciesInTest)
  .settings(
    reStart / javaOptions += "-Xmx256m",
    Test / parallelExecution := false,
    reStart                  := reStart.dependsOn(runnerRuntimeDependencies: _*).evaluated,
    resolvers ++= Resolver.sonatypeOssRepos("public"),
    libraryDependencies ++= Seq(
      akka("actor"),
      akka("testkit") % Test,
      akka("cluster"),
      akka("slf4j"),
      "org.scalameta" %% "scalafmt-core" % "3.6.1"
    ),
    docker / imageNames := Seq(
      ImageName(namespace = Some(dockerOrg), repository = "scastie-sbt-runner", tag = Some(gitHashNow)),
      ImageName(namespace = Some(dockerOrg), repository = "scastie-sbt-runner", tag = Some("latest"))
    ),
    docker / buildOptions := (docker / buildOptions).value
      .copy(additionalArguments = List("--add-host", "jenkins.scala-sbt.org:127.0.0.1")),
    docker / dockerfile := Def
      .task {
        DockerHelper(
          baseDirectory = (ThisBuild / baseDirectory).value.toPath,
          sbtTargetDir = target.value.toPath,
          ivyHome = ivyPaths.value.ivyHome.get.toPath,
          organization = organization.value,
          artifact = assembly.value.toPath,
          sbtScastie = (sbtScastie / moduleName).value,
          sbtVersion = sbtVersion.value
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
    fullLinkJS / reStart   := reStart.dependsOn(client / Compile / fullLinkJS / yarnBuild).evaluated,
    Universal / packageBin := (Universal / packageBin).dependsOn(client / Compile / fullLinkJS / yarnBuild).value,
    reStart / javaOptions += "-Xmx512m",
    maintainer := "scalacenter",
    libraryDependencies ++= Seq(
      "org.apache.commons"                  % "commons-text"   % "1.10.0",
      "com.typesafe.akka"                  %% "akka-http"      % akkaHttpVersion,
      "com.softwaremill.akka-http-session" %% "core"           % "0.7.0",
      "ch.megard"                          %% "akka-http-cors" % "1.1.3",
      akka("cluster"),
      akka("slf4j"),
      akka("testkit")      % Test,
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
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.7.2",
      "net.lingala.zip4j"  % "zip4j"              % "2.11.2"
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, instrumentation)

lazy val client = project
  .enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
  .settings(baseNoCrossSettings)
  .settings(baseJsSettings)
  .settings(
    externalNpm := {
      scala.sys.process.Process("yarn", baseDirectory.value.getParentFile) ! ProcessLogger(line => ())
      baseDirectory.value.getParentFile
    },
    stFlavour := Flavour.ScalajsReact,
    Compile / fastLinkJS / scalaJSLinkerConfig := {
      val dir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value.toURI()
      scalaJSLinkerConfig.value
        .withModuleKind(ModuleKind.ESModule)
        .withRelativizeSourceMapBase(Some(dir))
    },
    Compile / fullLinkJS / scalaJSLinkerConfig := {
      scalaJSLinkerConfig.value.withModuleKind(ModuleKind.ESModule)
    },
    yarnBuild := {
      scala.sys.process.Process("yarn build").!
    },
    test                        := {},
    Test / loadedTestFrameworks := Map(),
    stIgnore := List(
      "@sentry/browser",
      "@sentry/tracing",
      "react",
      "react-dom",
      "source-map-support"
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"  % "2.1.1",
      "com.github.japgolly.scalajs-react" %%% "extra" % "2.1.1"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(api.js(ScalaVersions.js))

lazy val instrumentation = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta"                 %% "scalameta" % "4.6.0",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0" % Test
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils)

lazy val api          = SbtShared.api
lazy val runtimeScala = SbtShared.`runtime-scala`

lazy val sbtScastie = project
  .in(file("sbt-scastie"))
  .settings(orgSettings)
  .settings(
    moduleName := "sbt-scastie",
    sbtPlugin  := true
  )
  .settings(version := versionRuntime)
  .dependsOn(api.jvm(ScalaVersions.sbt))
