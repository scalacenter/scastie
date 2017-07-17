import sbt._
import Keys._

import java.io.File
import java.nio.file._
import java.nio.file.attribute._

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.SbtNativePackager.Universal
import sbtdocker.DockerKeys.{docker, dockerBuildAndPush, imageNames}
import sbtdocker.ImageName

object Deployment {

  def gitIsDirty(): Boolean =
    Process("git diff-files --quiet").! == 1

  def gitHash(): String = {
    import sys.process._
    if (!sys.env.contains("CI")) {

      val indexState =
        if (gitIsDirty()) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lines.mkString("") + indexState
    } else "CI"
  }

  def settings(server: Project,
               sbtRunner: Project): Seq[Def.Setting[Task[Unit]]] = Seq(
    deploy := deployTask(server, sbtRunner).value,
    deployServer := deployServerTask(server, sbtRunner).value,
    deployQuick := deployQuickTask(server, sbtRunner).value,
    deployServerQuick := deployServerQuickTask(server, sbtRunner).value
  )

  lazy val deploy = taskKey[Unit]("Deploy server and sbt instances")

  lazy val deployServer = taskKey[Unit]("Deploy server")

  lazy val deployQuick = taskKey[Unit](
    "Deploy server and sbt instances without building server " +
      "zip and pushing docker images"
  )

  lazy val deployServerQuick =
    taskKey[Unit]("Deploy server without building server zip")

  def deployServerTask(server: Project,
                       sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath

      deployment.deployServer(serverZip)
      deployment.deployRunners()
    }

  def deployTask(server: Project,
                 sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath
      val imageId = (dockerBuildAndPush in sbtRunner).value

      deployment.deploy(serverZip)
    }

  def deployQuickTask(server: Project,
                      sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployQuick will not push the sbt-runner docker image nor create the server zip"
      )
      deployment.deploy(serverZip)
    }

  def deployServerQuickTask(server: Project,
                            sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployServerQuick will not create the server zip"
      )
      deployment.deployServer(serverZip)
      deployment.deployRunners()
    }

  private def deploymentTask(
      sbtRunner: Project
  ): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (baseDirectory in ThisBuild).value,
        version = version.value,
        dockerImage = (imageNames in (sbtRunner, docker)).value.head,
        logger = streams.value.log
      )
    }

  private def serverZipTask(server: Project): Def.Initialize[Task[Path]] =
    Def.task {
      val universalTarget = (target in (server, Universal)).value
      val universalName = (name in (server, Universal)).value
      val serverVersion = (version in server).value
      (universalTarget / (universalName + "-" + serverVersion + ".zip")).toPath
    }
}

class Deployment(rootFolder: File,
                 version: String,
                 dockerImage: ImageName,
                 val logger: Logger) {
  def deploy(serverZip: Path): Unit = {
    deployRunners()
    deployServer(serverZip)
  }

  def deployServer(serverZip: Path): Unit = {
    logger.info("Generate server script")

    val serverScriptDir = Files.createTempDirectory("server")
    val serverScript = serverScriptDir.resolve("server.sh")

    val productionConfigFileName = productionConfig.getFileName
    val logbackConfigFileName = logbackConfig.getFileName
    val serverZipFileName = serverZip.getFileName

    val secretConfig = getSecretConfig()
    val sentryDsn = getSentryDsn(secretConfig)

    val content =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |kill -9 `cat RUNNING_PID`
          |
          |unzip -d server $serverZipFileName
          |mv server/*/* server/
          |
          |nohup server/bin/server \\
          |  -Dconfig.file=/home/$userName/$applicationRootConfig \\
          |  -Dlogback.configurationFile=/home/$userName/$logbackConfigFileName \\
          |  -Dsentry.dsn=$sentryDsn \\
          |  -Dsentry.release=$version \\
          |  &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, content.getBytes)
    Files.setPosixFilePermissions(serverScript, executablePermissions)

    logger.info("Deploy servers")

    Process("rm -rf server*.sh") ! logger

    rsyncServer(secretConfig)
    rsyncServer(serverZip)
    rsyncServer(serverScript)
    rsyncServer(productionConfig)
    rsyncServer(logbackConfig)

    val scriptFileName = serverScript.getFileName
    val uri = userName + "@" + serverHostname
    Process(s"ssh $uri ./$scriptFileName") ! logger
  }

  def deployRunners(): Unit = {
    val dockerNamespace = dockerImage.namespace.get
    val dockerRepository = dockerImage.repository

    val sbtScriptDir = Files.createTempDirectory("sbt")
    val sbtScript = sbtScriptDir.resolve("sbt.sh")

    logger.info("Generate sbt script")

    val runnersPortsEnd = runnersPortsStart + runnersPortsSize

    val dockerImagePath =
      s"$dockerNamespace/$dockerRepository:${Deployment.gitHash()}"

    val sentryDsn = getSentryDsn(getSecretConfig())

    val sbtScriptContent =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |# kill all docker instances
          |docker kill $$(docker ps -q)
          |
          |docker rmi -f $dockerImagePath
          |
          |# Run all instances
          |for i in `seq $runnersPortsStart $runnersPortsEnd`;
          |do
          |  echo "Starting Runner: Port $$i"
          |  docker run --network=host -d \\
          |    -v /home/$userName/.coursier/cache:/drone/.coursier \\
          |    -e RUNNER_PRODUCTION=true \\
          |    -e RUNNER_PORT=$$i \\
          |    -e RUNNER_HOSTNAME=$runnersHostname \\
          |    -e SENTRY_DSN=$sentryDsn \\
          |    -e SENTRY_RELEASE=$version \\
          |    $dockerImagePath
          |done
          |""".stripMargin

    Files.write(sbtScript, sbtScriptContent.getBytes)
    Files.setPosixFilePermissions(sbtScript, executablePermissions)
    val scriptFileName = sbtScript.getFileName

    val runnerUri = userName + "@" + runnersHostname
    val serverUri = userName + "@" + serverHostname

    val proxyScript = sbtScriptDir.resolve("proxy.sh")
    val proxyScriptFileName = proxyScript.getFileName

    val proxyScriptContent =
      s"""|rm proxy*.sh
          |rsync $scriptFileName $runnerUri:$scriptFileName
          |ssh $runnerUri ./$scriptFileName
          |rm $scriptFileName""".stripMargin

    Files.write(proxyScript, proxyScriptContent.getBytes)
    Files.setPosixFilePermissions(proxyScript, executablePermissions)

    rsyncServer(sbtScript)
    rsyncServer(proxyScript)
    Process(s"ssh $serverUri ./$proxyScriptFileName") ! logger
  }

  private val applicationRootConfig = "application.conf"

  private def getSecretConfig(): Path = {
    val scastieSecrets = "scastie-secrets"
    val secretFolder = rootFolder / ".." / scastieSecrets

    if (Files.exists(secretFolder.toPath)) {
      Process("git pull origin master", secretFolder)
    } else {
      Process(s"git clone git@github.com:scalacenter/$scastieSecrets.git")
    }

    (secretFolder / applicationRootConfig).toPath
  }

  private def getSentryDsn(secretConfig: Path): String = {
    val config = ConfigFactory.parseFile(secretConfig.toFile)
    val scastieConfig = config.getConfig("com.olegych.scastie")
    scastieConfig.getString("sentry.dsn")
  }

  private val userName = "scastie"

  private val deploymentFolder = rootFolder / "deployment"

  private val productionConfig = (deploymentFolder / "production.conf").toPath
  private val logbackConfig = (deploymentFolder / "logback.xml").toPath

  private val config =
    ConfigFactory.parseFile(productionConfig.toFile)

  val balancerConfig = config.getConfig("com.olegych.scastie.balancer")

  private val serverHostname = config.getString("server-hostname")

  private val runnersHostname = balancerConfig.getString("remote-hostname")
  private val runnersPortsStart = balancerConfig.getInt("remote-ports-start")
  private val runnersPortsSize = balancerConfig.getInt("remote-ports-size")

  private val executablePermissions =
    PosixFilePermissions.fromString("rwxr-xr-x")

  private def rsync(file: Path,
                    userName: String,
                    hostname: String,
                    logger: Logger): Unit = {
    val uri = userName + "@" + hostname
    val fileName = file.getFileName
    Process(s"rsync $file $uri:$fileName") ! logger
  }

  private def rsyncServer(file: Path) =
    rsync(file, userName, serverHostname, logger)
}
