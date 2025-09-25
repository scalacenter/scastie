import scala.sys.process._
import scala.sys.process.ProcessLogger

import com.typesafe.sbt.SbtNativePackager.Universal
import org.scalajs.linker.interface.ModuleSplitStyle
import SbtShared._

def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.6.19"

val akkaHttpVersion = "10.2.9"

addCommandAlias("startAll", "scalaCliRunner/reStart;sbtRunner/reStart;server/reStart;metalsRunner/reStart;client/fastLinkJS")
addCommandAlias("startAllProd", "scalaCliRunner/reStart;sbtRunner/reStart;metalsRunner/reStart;server/buildTreesitterWasm;server/fullLinkJS/reStart")

val yarnBuild = taskKey[Unit]("builds es modules with `yarn build`")

logo := Welcome.logo
usefulTasks := Welcome.tasks

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
      metalsRunner,
      scalaCliRunner
    ).map(_.project)): _*
  )
  .settings(baseSettings)
  .settings(
    cachedCiTestFull := {
      val _     = cachedCiTestFull.dependsOn(publishLocal).value
      val __    = (sbtRunner / docker / dockerfile).value
      val ___   = (scalaCliRunner / docker / dockerfile).value
      val ____  = (metalsRunner / docker / dockerfile).value
      val _____ = (server / Universal / packageBin).value
    }
  )
  .settings(Deployment.settings(server, sbtRunner, scalaCliRunner, metalsRunner))

lazy val testSettings = Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

lazy val loggingAndTest = Seq(
  libraryDependencies ++= Seq(
    "ch.qos.logback"              % "logback-classic" % "1.4.14",
    "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",
    "io.sentry"                   % "sentry-logback"  % "6.34.0"
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

lazy val runnerRuntimeDependencies = (api.projectRefs ++ runtimeScala.projectRefs ++ runner.projectRefs ++ Seq(
  sbtScastie.project
)).map(_ / publishLocal)

lazy val runnerRuntimeDependenciesInTest = Seq(
  assembly / test  := {},
  Test / test      := (Test / test).dependsOn(runnerRuntimeDependencies: _*).value,
  Test / testOnly  := (Test / testOnly).dependsOn(runnerRuntimeDependencies: _*).evaluated,
  Test / testQuick := (Test / testQuick).dependsOn(runnerRuntimeDependencies: _*).evaluated
)

lazy val smallRunnerRuntimeDependenciesInTest = {
  lazy val smallRunnerRuntimeDependencies = Seq(api.jvm(ScalaVersions.jvm),
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
    javacOptions ++= Seq("-Xms1G", "-Xmx12G", "-XX:+CrashOnOutOfMemoryError"),
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
    scalaVersion := ScalaVersions.stableLTS,
    libraryDependencies ++= Seq(
      "org.scalameta"        % "metals"              % "1.4.2" cross (CrossVersion.for3Use2_13),
      "org.eclipse.lsp4j"    % "org.eclipse.lsp4j"   % "0.21.1",
      "org.http4s"          %% "http4s-ember-server" % "0.23.24",
      "org.http4s"          %% "http4s-ember-client" % "0.23.24",
      "org.http4s"          %% "http4s-dsl"          % "0.23.24",
      "org.http4s"          %% "http4s-circe"        % "0.23.24",
      "io.circe"            %% "circe-generic"       % "0.14.6",
      "com.evolutiongaming" %% "scache"              % "4.2.3",
      "org.scalameta"       %% "munit"               % "0.7.29" % Test,
      "org.typelevel"       %% "munit-cats-effect-3" % "1.0.7"  % Test,
      "org.virtuslab"        % "using_directives"    % "1.1.0",
      "io.circe" %% "circe-parser" % "0.14.6",
      "io.get-coursier" %% "coursier" % "2.1.6" cross (CrossVersion.for3Use2_13),
    )
  )
  .enablePlugins(JavaServerAppPackaging, sbtdocker.DockerPlugin)
  .dependsOn(api.jvm(ScalaVersions.stableNext))

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
      "org.scalameta" %% "scalafmt-core" % "3.9.2",
      "io.circe" %% "circe-parser" % "0.14.6"
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

lazy val buildTreesitterWasm = taskKey[Seq[File]]("Builds tree-sitter-scala.wasm")

lazy val server = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    watchSources ++= (client / watchSources).value,
    Compile / products += (client / baseDirectory).value / "dist",
    fullLinkJS / reStart := reStart.dependsOn(client / Compile / fullLinkJS / yarnBuild).evaluated,
    Universal / packageBin := (Universal / packageBin)
      .dependsOn(buildTreesitterWasm)
      .dependsOn(client / Compile / fullLinkJS / yarnBuild)
      .value,
    reStart / javaOptions += "-Xmx512m",
    maintainer := "scalacenter",
    buildTreesitterWasm := {
      val treeSitterOutputName = "tree-sitter.wasm"
      val treeSitterWasm = baseDirectory.value.getParentFile / "node_modules" / "web-tree-sitter" / treeSitterOutputName

      val treeSitterScalaOutputName = "tree-sitter-scala.wasm"
      val treeSitterScalaWasm = baseDirectory.value.getParentFile / "tree-sitter-scala" / treeSitterScalaOutputName

      val treeSitterScalaHiglightName = "highlights.scm"
      val treeSitterScalaHiglight =
        baseDirectory.value.getParentFile / "tree-sitter-scala" / "queries" / treeSitterScalaHiglightName

      val outputWasmDirectory = (Compile / resourceDirectory).value / "public"

      val s: TaskStreams     = streams.value
      val shell: Seq[String] = if (sys.props("os.name").contains("Windows")) Seq("cmd", "/c") else Seq("bash", "-c")
      val updateGitSubmodule: Seq[String] = shell :+ "git submodule update --init"

      val installNpmDependencies: Seq[String] = shell :+ "npm install && cd tree-sitter-scala && npm install"
      val buildWasm: Seq[String] = shell :+ "cd tree-sitter-scala && npx tree-sitter build --wasm ."
      s.log.info("building tree-sitter-scala wasm...")

      val updateGitSubmoduleExit = Process(updateGitSubmodule, baseDirectory.value.getParentFile)
        .!(ProcessLogger(line => s.log.info(s"[git submodule] $line"), err => s.log.info(s"[git submodule] $err")))
      if (updateGitSubmoduleExit != 0) {
        throw new IllegalStateException(s"Failed to update git submodule!")
      }

      val installNpmDependenciesExit = Process(installNpmDependencies, baseDirectory.value.getParentFile)
        .!(ProcessLogger(line => s.log.info(s"[npm install] $line"), err => s.log.info(s"[npm install] $err")))
      if (installNpmDependenciesExit != 0) {
        throw new IllegalStateException(s"Failed to install npm dependencies!")
      }

      val buildWasmExitCode = Process(buildWasm, baseDirectory.value.getParentFile)
        .!(ProcessLogger(line => s.log.info(s"[tree-sitter build] $line"), err => s.log.info(s"[tree-sitter build] $err")))
      if (buildWasmExitCode != 0) {
        throw new IllegalStateException("tree-sitter build failed!")
      } else {
        s.log.success(s"$treeSitterOutputName build successfuly!")
      }

      sbt.IO.copyFile(treeSitterScalaHiglight, outputWasmDirectory / treeSitterScalaHiglightName)
      sbt.IO.move(treeSitterScalaWasm, outputWasmDirectory / treeSitterScalaOutputName)
      sbt.IO.copyFile(treeSitterWasm, outputWasmDirectory / treeSitterOutputName)
      s.log.success(
        s"Copied $treeSitterScalaHiglight to ${(outputWasmDirectory / treeSitterScalaHiglightName).getAbsolutePath}"
      )
      s.log.success(
        s"Copied $treeSitterScalaWasm to ${(outputWasmDirectory / treeSitterScalaOutputName).getAbsolutePath}"
      )
      s.log.success(s"Copied $treeSitterWasm to ${(outputWasmDirectory / treeSitterOutputName).getAbsolutePath}")
      Seq(outputWasmDirectory / treeSitterScalaOutputName, outputWasmDirectory / treeSitterOutputName)
    },
    libraryDependencies ++= Seq(
      "org.apache.commons"                  % "commons-text"   % "1.11.0",
      "com.typesafe.akka"                  %% "akka-http"      % akkaHttpVersion,
      "com.softwaremill.akka-http-session" %% "core"           % "0.7.1",
      "ch.megard"                          %% "akka-http-cors" % "1.2.0",
      "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
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
    scalacOptions += "-Ywarn-unused",
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.11.1",
      "net.lingala.zip4j"  % "zip4j"              % "2.11.5",
      "io.circe" %% "circe-parser" % "0.14.6"
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils, instrumentation)

lazy val yarnBin =
  if (scala.util.Properties.isWin) "yarn.cmd"
  else "yarn"

lazy val client = project
  .enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
  .settings(baseNoCrossSettings)
  .settings(baseJsSettings)
  .settings(
    externalNpm := {
      Process(yarnBin, baseDirectory.value.getParentFile) ! ProcessLogger(line => ())
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
      Process(s"$yarnBin build").!
    },
    test                        := {},
    Test / loadedTestFrameworks := Map(),
    stIgnore := List(
      "@sentry/browser",
      "@sentry/tracing",
      "github-markdown-css",
      "react",
      "react-dom",
      "source-map-support",
      "vite"
    ),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"                        % "2.1.1",
      "com.github.japgolly.scalajs-react" %%% "extra"                       % "2.1.1",
      "org.scala-js"                      %%% "scala-js-macrotask-executor" % "1.1.1",
      "io.circe"                          %%% "circe-parser"                % "0.14.6",
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(runtimeApi.js(ScalaVersions.js))
  .dependsOn(api.js(ScalaVersions.js))

lazy val instrumentation = project
  .settings(baseNoCrossSettings)
  .settings(loggingAndTest)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta"                 %% "scalameta" % "4.12.6",
      "com.googlecode.java-diff-utils" % "diffutils" % "1.3.0"
    )
  )
  .dependsOn(api.jvm(ScalaVersions.jvm), utils)

lazy val runtimeApi   = SbtShared.`runtime-api`
lazy val runtimeScala = SbtShared.`runtime-scala`
lazy val api          = SbtShared.api

lazy val sbtScastie = project
  .in(file("sbt-scastie"))
  .settings(orgSettings)
  .settings(
    moduleName := "sbt-scastie",
    sbtPlugin  := true,
    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.6"
  )
  .settings(version := versionRuntime)
  .dependsOn(api.jvm(ScalaVersions.sbt))

lazy val runner = projectMatrix
  .in(file("runner"))
  .jvmPlatform(Seq(ScalaVersions.latest213, ScalaVersions.latest212, ScalaVersions.old3))
  .settings(SbtShared.baseSettings)
  .settings(
     version := SbtShared.versionRuntime,
     Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "runtime-api",
  )

lazy val scalaCliRunner = project
  .in(file("scala-cli-runner"))
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
      "ch.epfl.scala" % "bsp4j" % "2.1.0-M7",
      "org.typelevel" %% "cats-core" % "2.10.0",
      "io.circe" %% "circe-parser" % "0.14.6",
      "io.get-coursier" %% "coursier" % "2.1.6",
    ),
    docker / imageNames := Seq(
      ImageName(namespace = Some(dockerOrg), repository = "scastie-scala-cli-runner", tag = Some(gitHashNow)),
      ImageName(namespace = Some(dockerOrg), repository = "scastie-scala-cli-runner", tag = Some("latest"))
    ),
    docker / dockerfile := Def
      .task {
        DockerHelper.scalaCliRunner(
          baseDirectory = (ThisBuild / baseDirectory).value.toPath,
          scalaCliTargetDir = target.value.toPath,
          ivyHome = ivyPaths.value.ivyHome.get.toPath,
          organization = organization.value,
          artifact = assembly.value.toPath,
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
  .enablePlugins(BuildInfoPlugin, sbtdocker.DockerPlugin)
  .dependsOn(api.jvm(ScalaVersions.jvm), instrumentation, utils)
