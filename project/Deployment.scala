import sbt._
import Keys._

import SbtShared.gitHashNow

import java.io.File
import java.nio.file._
import java.nio.file.attribute._

import com.typesafe.config.ConfigFactory
import com.typesafe.sbt.SbtNativePackager.Universal
import sbtdocker.DockerKeys.{docker, dockerBuildAndPush, imageNames}
import sbtdocker.ImageName

object Deployment {
  def settings(server: Project,
               sbtRunner: Project,
               ensimeRunner: Project): Seq[Def.Setting[Task[Unit]]] = Seq(
    deploy := deployTask(server, sbtRunner, ensimeRunner).value,
    deployServer := deployServerTask(server, sbtRunner, ensimeRunner).value,
    deployQuick := deployQuickTask(server, sbtRunner, ensimeRunner).value,
    deployServerQuick := deployServerQuickTask(server, sbtRunner, ensimeRunner).value
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
                       sbtRunner: Project,
                       ensimeRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, ensimeRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath

      deployment.deployServer(serverZip)
      deployment.deployRunners()
    }

  def deployTask(server: Project,
                 sbtRunner: Project,
                 ensimeRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, ensimeRunner).value
      val serverZip = (packageBin in (server, Universal)).value.toPath
      val imageIdSbt = (dockerBuildAndPush in sbtRunner).value
      val imageIdEnsime = (dockerBuildAndPush in ensimeRunner).value

      deployment.deploy(serverZip)
    }

  def deployQuickTask(server: Project,
                      sbtRunner: Project,
                      ensimeRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, ensimeRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployQuick will not push the sbt-runner docker image nor create the server zip"
      )
      deployment.deploy(serverZip)
    }

  def deployServerQuickTask(server: Project,
                            sbtRunner: Project,
                            ensimeRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, ensimeRunner).value
      val serverZip = serverZipTask(server).value

      deployment.logger.warn(
        "deployServerQuick will not create the server zip"
      )
      deployment.deployServer(serverZip)
      deployment.deployRunners()
    }

  private def deploymentTask(
      sbtRunner: Project,
      ensimeRunner: Project
  ): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (baseDirectory in ThisBuild).value,
        version = version.value,
        sbtDockerImage = (imageNames in (sbtRunner, docker)).value.head,
        ensimeDockerImage = (imageNames in (ensimeRunner, docker)).value.head,
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
                 sbtDockerImage: ImageName,
                 ensimeDockerImage: ImageName,
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
    val sbtDockerNamespace = sbtDockerImage.namespace.get
    val sbtDockerRepository = sbtDockerImage.repository

    val ensimeDockerNamespace = ensimeDockerImage.namespace.get
    val ensimeDockerRepository = ensimeDockerImage.repository

    killRunners()

    deployRunners(
      "sbt",
      s"$sbtDockerNamespace/$sbtDockerRepository",
      sbtRunnersPortsStart,
      sbtRunnersPortsSize
    )

    deployRunners(
      "ensime",
      s"$ensimeDockerNamespace/$ensimeDockerRepository",
      ensimeRunnersPortsStart,
      ensimeRunnersPortsSize
    )
  }

  def killRunners(): Unit = {
  // |# kill all docker instances
  // |

    val killScriptDir = Files.createTempDirectory("kill")
    val killScript = killScriptDir.resolve("kill.sh")

    logger.info(s"Generate kill script")

    
    val killScriptContent =
      s"""|#!/usr/bin/env bash
          |
          |docker kill $$(docker ps -q)
          |""".stripMargin

    Files.write(killScript, killScriptContent.getBytes)
    Files.setPosixFilePermissions(killScript, executablePermissions)
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
    Files.setPosixFilePermissions(proxyScript, executablePermissions)

    rsyncServer(killScript)
    rsyncServer(proxyScript)
    Process(s"ssh $serverUri ./$proxyScriptFileName") ! logger
  }


  def deployRunners(
    runner: String,
    image: String,
    runnersPortsStart: Int,
    runnersPortsSize: Int): Unit = {

    val runnerScriptDir = Files.createTempDirectory(runner)
    val runnerScript = runnerScriptDir.resolve(runner + ".sh")

    logger.info(s"Generate $runner script")

    val runnersPortsEnd = runnersPortsStart + runnersPortsSize

    val dockerImagePath = s"$image:$gitHashNow"

    val sentryDsn = getSentryDsn(getSecretConfig())

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
          |    --network=host \\
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
    Files.setPosixFilePermissions(runnerScript, executablePermissions)
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
    Files.setPosixFilePermissions(proxyScript, executablePermissions)

    rsyncServer(runnerScript)
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

  private val serverConfig = config.getConfig("com.olegych.scastie.web")
  private val serverHostname = serverConfig.getString("hostname")
  private val serverAkkaPort = serverConfig.getInt("akka-port")

  private val runnersHostname = balancerConfig.getString("remote-hostname")

  private val sbtRunnersPortsStart = balancerConfig.getInt("remote-sbt-ports-start")
  private val sbtRunnersPortsSize = balancerConfig.getInt("remote-sbt-ports-size")

  private val ensimeRunnersPortsStart = balancerConfig.getInt("remote-ensime-ports-start")
  private val ensimeRunnersPortsSize = balancerConfig.getInt("remote-ensime-ports-size")

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
