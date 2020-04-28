import sbt._
import Keys._

import SbtShared.gitHashNow

import java.io.File
import java.nio.file._
import java.nio.file.attribute._
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.SbtNativePackager.Universal
import sbtdocker.DockerKeys.{docker, dockerBuildAndPush, imageNames}
import sbtdocker.ImageName
import sys.process._

object Deployment {
  def settings(server: Project, sbtRunner: Project): Seq[Def.Setting[Task[Unit]]] = Seq(
    deploy := deployTask(server, sbtRunner).value,
    deployServer := deployServerTask(server, sbtRunner).value,
    deployQuick := deployQuickTask(server, sbtRunner).value,
    deployServerQuick := deployServerQuickTask(server, sbtRunner).value,
    deployLocal := deployLocalTask(server, sbtRunner).value
  )

  lazy val deploy = taskKey[Unit]("Deploy server and sbt instances")

  lazy val deployServer = taskKey[Unit]("Deploy server")

  lazy val deployLocal = taskKey[Unit]("Deploy locally")

  lazy val deployQuick = taskKey[Unit](
    "Deploy server and sbt instances without building server " +
      "zip and pushing docker images"
  )

  lazy val deployServerQuick =
    taskKey[Unit]("Deploy server without building server zip")

  def deployServerTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath

      deployment.deployServer(serverZip)
    }

  def deployLocalTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath
      val imageIdSbt = (docker in sbtRunner).value

      deployment.deployLocal(serverZip)
    }

  def deployTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath
      val imageIdSbt = (dockerBuildAndPush in sbtRunner).value

      deployment.deploy(serverZip)
    }

  def deployQuickTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployQuick will not push the sbt-runner docker image nor create the server zip"
      )
      deployment.deploy(serverZip)
    }

  def deployServerQuickTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployServerQuick will not create the server zip"
      )
      deployment.deployServer(serverZip)
    }

  private def deploymentTask(
      sbtRunner: Project
  ): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (baseDirectory in ThisBuild).value,
        version = version.value,
        sbtDockerImage = (imageNames in (sbtRunner, docker)).value.head,
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

class Deployment(rootFolder: File, version: String, sbtDockerImage: ImageName, val logger: Logger) {
  def deploy(serverZip: Path): Unit = {
    deployRunners()
    deployServer(serverZip)
  }

  def deployLocal(serverZip: Path): Unit = {
    val sbtDockerNamespace = sbtDockerImage.namespace.get
    val sbtDockerRepository = sbtDockerImage.repository

    val destination = rootFolder.toPath.resolve("local")

    if (!Files.exists(destination)) {
      Files.createDirectory(destination)
    }

    val snippetsFolder = destination.resolve("snippets")

    if (!Files.exists(snippetsFolder)) {
      Files.createDirectory(snippetsFolder)
    }

    val deploymentFiles =
      deployServerFiles(serverZip, destination, local = true)

    deploymentFiles.files.foreach(
      file =>
        Files
          .copy(file, destination.resolve(file.getFileName), REPLACE_EXISTING)
    )

    val runnerScriptContent =
      s"""|#!/usr/bin/env bash
          |
          |docker run \\
          |  --network=host \\
          |  -e RUNNER_PORT=5150 \\
          |  -e RUNNER_HOSTNAME=127.0.0.1 \\
          |  -e RUNNER_RECONNECT=false \\
          |  -e RUNNER_PRODUCTION=true \\
          |  $sbtDockerNamespace/$sbtDockerRepository:$gitHashNow
          |
          |""".stripMargin

    val runnerScript = destination.resolve("sbt.sh")

    Files.write(runnerScript, runnerScriptContent.getBytes)
    setPosixFilePermissions(runnerScript, executablePermissions)
  }

  def deployServer(serverZip: Path): Unit = {
    val serverScriptDir = Files.createTempDirectory("server")

    val deploymentFiles =
      deployServerFiles(serverZip, serverScriptDir, local = false)

    deploymentFiles.files.foreach(rsyncServer)

    val scriptFileName = deploymentFiles.serverScript.getFileName
    val uri = userName + "@" + serverHostname
    Process(s"ssh $uri ./$scriptFileName") ! logger
  }

  case class DeploymentFiles(
      secretConfig: Path,
      serverZip: Path,
      serverScript: Path,
      productionConfig: Path,
      logbackConfig: Path
  ) {
    def files: List[Path] = List(
      secretConfig,
      serverZip,
      serverScript,
      productionConfig,
      logbackConfig
    )
  }

  private def deployServerFiles(serverZip: Path, destination: Path, local: Boolean): DeploymentFiles = {
    logger.info("Generate server script")

    val serverScript = destination.resolve("server.sh")

    val config =
      if (local) localConfig
      else productionConfig

    val configFileName = config.getFileName
    val logbackConfigFileName = logbackConfig.getFileName
    val serverZipFileName = serverZip.getFileName.toString.replace(".zip", "")

    val secretConfig = getSecretConfig()
    val sentryDsn = getSentryDsn(secretConfig)

    val baseDir =
      if (!local) s"/home/$userName/"
      else ""

    val content =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |serverZipFileName=$serverZipFileName
          |
          |kill -9 `cat ${baseDir}RUNNING_PID`
          |
          |rm -rf ${baseDir}server/*
          |unzip -o -d ${baseDir}server ${baseDir}$$serverZipFileName
          |mv ${baseDir}server/$$serverZipFileName/* ${baseDir}server/
          |rm -rf ${baseDir}server/$$serverZipFileName
          |
          |nohup ${baseDir}server/bin/server \\
          |  -J-Xmx1G \\
          |  -Dconfig.file=${baseDir}${configFileName} \\
          |  -Dlogback.configurationFile=${baseDir}${logbackConfigFileName} \\
          |  -Dsentry.dsn=$sentryDsn \\
          |  -Dsentry.release=$version \\
          |  &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, content.getBytes)
    setPosixFilePermissions(serverScript, executablePermissions)

    logger.info("Deploy servers")

    DeploymentFiles(
      secretConfig,
      serverZip,
      serverScript,
      config,
      logbackConfig
    )
  }

  def deployRunners(): Unit = {
    val sbtDockerNamespace = sbtDockerImage.namespace.get
    val sbtDockerRepository = sbtDockerImage.repository

    killRunners()

    deployRunners(
      "sbt",
      s"$sbtDockerNamespace/$sbtDockerRepository",
      sbtRunnersPortsStart,
      sbtRunnersPortsSize
    )
  }

  def killRunners(): Unit = {
    val killScriptDir = Files.createTempDirectory("kill")
    val killScript = killScriptDir.resolve("kill.sh")

    logger.info(s"Generate kill script")

    val killScriptContent =
      """|#!/usr/bin/env bash
         |
         |# Delete all containers
         |docker rm $(docker ps -a -q)
         |
         |# Delete all images
         |docker rmi $(docker images -q)
         |
         |docker kill $(docker ps -q)
         |""".stripMargin

    Files.write(killScript, killScriptContent.getBytes)
    setPosixFilePermissions(killScript, executablePermissions)
    val scriptFileName = killScript.getFileName

    val runnerUri = userName + "@" + runnersHostname
    val serverUri = userName + "@" + serverHostname

    val proxyScript = killScriptDir.resolve("kill-proxy.sh")
    val proxyScriptFileName = proxyScript.getFileName

    val proxyScriptContent =
      s"""|rm kill-proxy.sh
          |rsync $scriptFileName $runnerUri:$scriptFileName
          |ssh $runnerUri ./$scriptFileName
          |rm $scriptFileName""".stripMargin

    Files.write(proxyScript, proxyScriptContent.getBytes)
    setPosixFilePermissions(proxyScript, executablePermissions)

    rsyncServer(killScript)
    rsyncServer(proxyScript)
    Process(s"ssh $serverUri ./$proxyScriptFileName") ! logger
  }

  def deployRunners(runner: String, image: String, runnersPortsStart: Int, runnersPortsSize: Int): Unit = {

    val runnerScriptDir = Files.createTempDirectory(runner)
    val runnerScript = runnerScriptDir.resolve(runner + ".sh")

    logger.info(s"Generate $runner script")

    val runnersPortsEnd = runnersPortsStart + (runnersPortsSize - 1)

    val dockerImagePath = s"$image:$gitHashNow"

    val sentryDsn = getSentryDsn(getSecretConfig())

    //jenkins.scala-sbt.org points to 127.0.0.1 to workaround https://github.com/sbt/sbt/issues/5458 and https://github.com/sbt/sbt/issues/5456
    val runnerScriptContent =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |
          |docker rmi -f $dockerImagePath
          |
          |# Run all instances
          |for i in `seq $runnersPortsStart $runnersPortsEnd`;
          |do
          |  echo "Starting Runner: Port $$i"
          |  docker run \\
          |    --add-host jenkins.scala-sbt.org:127.0.0.1 \\
          |    --network=host \\
          |    --restart=always \\
          |    -d \\
          |    -e RUNNER_PRODUCTION=true \\
          |    -e RUNNER_PORT=$$i \\
          |    -e SERVER_HOSTNAME=$serverHostname \\
          |    -e SERVER_AKKA_PORT=$serverAkkaPort \\
          |    -e RUNNER_HOSTNAME=$runnersHostname \\
          |    -e SENTRY_DSN=$sentryDsn \\
          |    -e SENTRY_RELEASE=$version \\
          |    $dockerImagePath
          |done
          |""".stripMargin

    Files.write(runnerScript, runnerScriptContent.getBytes)
    setPosixFilePermissions(runnerScript, executablePermissions)
    val scriptFileName = runnerScript.getFileName

    val runnerUri = userName + "@" + runnersHostname
    val serverUri = userName + "@" + serverHostname

    val proxyScript = runnerScriptDir.resolve(runner + "-proxy.sh")
    val proxyScriptFileName = proxyScript.getFileName

    val proxyScriptContent =
      s"""|rm ${runner}-proxy.sh
          |rsync $scriptFileName $runnerUri:$scriptFileName
          |ssh $runnerUri ./$scriptFileName
          |rm $scriptFileName""".stripMargin

    Files.write(proxyScript, proxyScriptContent.getBytes)
    setPosixFilePermissions(proxyScript, executablePermissions)

    rsyncServer(runnerScript)
    rsyncServer(proxyScript)
    Process(s"ssh $serverUri ./$proxyScriptFileName") ! logger
  }

  private def getSecretConfig(): Path = {
    val scastieSecrets = "scastie-secrets"
    val secretFolder = rootFolder / ".." / scastieSecrets

    if (Files.exists(secretFolder.toPath)) {
      Process("git pull origin master", secretFolder)
    } else {
      Process(s"git clone git@github.com:scalacenter/$scastieSecrets.git")
    }

    (secretFolder / "secrets.conf").toPath
  }

  private def getSentryDsn(secretConfig: Path): String = {
    val config = ConfigFactory.parseFile(secretConfig.toFile)
    val scastieConfig = config.getConfig("com.olegych.scastie")
    scastieConfig.getString("sentry.dsn")
  }

  private val userName = "scastie"

  private val deploymentFolder = rootFolder / "deployment"

  private val productionConfig = (deploymentFolder / "production.conf").toPath
  private val localConfig = (deploymentFolder / "local.conf").toPath

  private val logbackConfig = (deploymentFolder / "logback.xml").toPath

  private val config =
    ConfigFactory.parseFile(productionConfig.toFile)

  val balancerConfig = config.getConfig("com.olegych.scastie.balancer")

  private val serverConfig = config.getConfig("com.olegych.scastie.web")
  private val serverHostname = serverConfig.getString("hostname")
  private val serverAkkaPort = serverConfig.getInt("akka-port")

  private val runnersHostname = balancerConfig.getString("remote-hostname")

  private val sbtRunnersPortsStart =
    balancerConfig.getInt("remote-sbt-ports-start")
  private val sbtRunnersPortsSize =
    balancerConfig.getInt("remote-sbt-ports-size")

  private val executablePermissions =
    PosixFilePermissions.fromString("rwxr-xr-x")

  private def rsync(file: Path, userName: String, hostname: String, logger: Logger): Unit = {
    val uri = userName + "@" + hostname
    val fileName = file.getFileName
    Process(s"rsync $file $uri:$fileName") ! logger
  }

  private def rsyncServer(file: Path) =
    rsync(file, userName, serverHostname, logger)

  private def setPosixFilePermissions(
      path: Path,
      perms: java.util.Set[PosixFilePermission]
  ): Path = {
    try Files.setPosixFilePermissions(path, perms)
    catch {
      case e: Exception => path
    }
  }
}
