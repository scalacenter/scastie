import sbt._
import Keys._

import java.io.File
import java.nio.file._
import java.nio.file.attribute._

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.SbtNativePackager.Universal
import sbtdocker.DockerKeys.{docker, dockerBuildAndPush, imageNames}
import sbtdocker.ImageName

// TODO deploy scastie-secrets

object Deployment {
  lazy val deploy = taskKey[Unit]("Deploy server and sbt instances")

  lazy val deployServer = taskKey[Unit]("Deploy server")

  lazy val deployQuick = taskKey[Unit](
    "Deploy server and sbt instances without building server " +
      "zip and pushing docker images")

  lazy val deployServerQuick =
    taskKey[Unit]("Deploy server without building server zip")

  def deployServerTask(server: Project,
                       sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath

      deployment.deployServer(serverZip)
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
    }

  private def deploymentTask(
      sbtRunner: Project): Def.Initialize[Task[Deployment]] =
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
      (universalTarget / (universalName + ".zip")).toPath
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

    val serverScript = Files.createTempFile("server", ".sh")
    Files.setPosixFilePermissions(serverScript, executablePermissions)

    val productionConfigFileName = productionConfig.getFileName
    val logbackConfigFileName = logbackConfig.getFileName
    val serverZipFileName = serverZip.getFileName

    val content =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |kill `cat server/RUNNING_PID`
          |rm -rf server
          |rm server*.sh
          |
          |unzip -d server $serverZipFileName
          |mv server/*/* server/
          |
          |nohup server/bin/server \\
          |  -Dconfig.file=/home/$userName/$productionConfigFileName \\
          |  -Dlogger.file=/home/$userName/$logbackConfigFileName &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, content.getBytes)

    logger.info("Deploy servers")

    def rsyncServer(file: Path) = rsync(file, userName, serverHostname, logger)

    Process("rm -rf server*.sh") ! logger

    rsyncServer(serverZip)
    rsyncServer(serverScript)
    rsyncServer(productionConfig)
    rsyncServer(logbackConfig)

    val scriptFileName = serverScript.getFileName
    val uri = userName + "@" + serverHostname
    Process(s"ssh $uri ./$scriptFileName") ! logger
  }

  private def deployRunners(): Unit = {
    val dockerNamespace = dockerImage.namespace.get
    val dockerRepository = dockerImage.repository

    val sbtScript = Files.createTempFile("sbt", ".sh")
    Files.setPosixFilePermissions(sbtScript, executablePermissions)

    logger.info("Generate sbt script")

    val runnersPortsEnd = runnersPortsStart + runnersPortsSize

    val dockerImagePath = s"$dockerNamespace/$dockerRepository:$version"

    val content =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |# kill all docker instances
          |docker kill $$(docker ps -q)
          |
          |docker rmi -f $dockerImagePath
          |
          |rm sbt*.sh
          |
          |# Run all instances
          |for i in `seq $runnersPortsStart $runnersPortsEnd`;
          |do
          |  echo "Starting Runner: Port $$i"
          |  docker run --network=host -d \\
          |    -v /home/$userName/.coursier/cache:/root/.coursier/cache \\
          |    -e RUNNER_PRODUCTION=1 \\
          |    -e RUNNER_PORT=$$i \\
          |    -e RUNNER_HOSTNAME=$runnersHostname \\
          |    $dockerImagePath
          |done
          |""".stripMargin

    Files.write(sbtScript, content.getBytes)

    val scriptFileName = sbtScript.getFileName
    val uri = userName + "@" + runnersHostname
    Process("rm -rf sbt*.sh") ! logger
    Process(s"rsync $sbtScript $uri:$scriptFileName") ! logger
    Process(s"ssh $uri ./$scriptFileName") ! logger
  }

  private val userName = "scastie"

  private val deploymentFolder = rootFolder / "deployment"

  private val productionConfig = (deploymentFolder / "production.conf").toPath
  private val logbackConfig = (deploymentFolder / "logback.xml").toPath

  private val config = ConfigFactory.parseFile(productionConfig.toFile)
  private val serverHostname = config.getString("server-hostname")
  private val runnersHostname = config.getString("sbt-remote-hostname")
  private val runnersPortsStart = config.getInt("sbt-remote-ports-start")
  private val runnersPortsSize = config.getInt("sbt-remote-ports-size")

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
}
