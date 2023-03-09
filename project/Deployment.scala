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
import java.time.LocalDateTime
import com.typesafe

object Deployment {
  def settings(server: Project, sbtRunner: Project, metalsRunner: Project): Seq[Def.Setting[Task[Unit]]] = Seq(
    deploy := deployTask(server, sbtRunner, metalsRunner, Production).value,
    deployStaging := deployTask(server, sbtRunner, metalsRunner, Staging).value,
    publishContainers := publishContainers(sbtRunner, metalsRunner).value,
    generateDeploymentScripts := generateDeploymentScriptsTask(server, sbtRunner, metalsRunner).value,
    deployLocal := deployLocalTask(server, sbtRunner, metalsRunner).value
  )

  lazy val deploy = taskKey[Unit]("Deploy server and sbt instances")
  lazy val deployStaging = taskKey[Unit]("Deploy server and sbt instances with staging configuration")
  lazy val publishContainers = taskKey[Unit]("Publishes sbt runners and metals runner to docker repository")
  lazy val generateDeploymentScripts = taskKey[Unit]("Generates deployment scripts with production configuration.")
  lazy val deployLocal = taskKey[Unit]("Deploy locally")

  def deployTask(server: Project, sbtRunner: Project, metalsRunner: Project, deploymentType: DeploymentType): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, metalsRunner, deploymentType).value
      val serverZip = (server / Universal / packageBin).value.toPath

      deployment.deploy(serverZip)
    }

  def publishContainers(sbtRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      (sbtRunner / dockerBuildAndPush).value
      (metalsRunner / dockerBuildAndPush).value
    }

  def generateDeploymentScriptsTask(server: Project, sbtRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, metalsRunner, Production).value
      deployment.generateDeploymentScripts()
    }


  def deployLocalTask(server: Project, sbtRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner, metalsRunner, Local).value
      val serverZip = (server / Universal / packageBin).value.toPath
      (sbtRunner / docker).value
      (metalsRunner / docker).value

      deployment.deployLocal(serverZip)
    }

  private def deploymentTask(sbtRunner: Project, metalsRunner: Project, deploymentType: DeploymentType): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (ThisBuild / baseDirectory).value,
        version = version.value,
        sbtDockerImage = (sbtRunner / docker / imageNames).value.head,
        metalsDockerImage = (metalsRunner / docker / imageNames).value.head,
        deploymentType = deploymentType,
        logger = streams.value.log
      )
    }
}

sealed trait DeploymentType
case object Local extends DeploymentType
case object Staging extends DeploymentType
case object Production extends DeploymentType

class ScastieConfig(val configurationFile: File) {
  val config = ConfigFactory.parseFile(configurationFile)
  val userName = "scastie"

  val serverConfig = config.getConfig("com.olegych.scastie.web")
  val serverHostname = serverConfig.getString("hostname")
  val serverAkkaPort = serverConfig.getInt("akka-port")

  val metalsConfig = config.getConfig("scastie.metals")
  val metalsPort = metalsConfig.getInt("port")
  val cacheExpireInSeconds = metalsConfig.getInt("cache-expire-in-seconds")

  val balancerConfig = config.getConfig("com.olegych.scastie.balancer")
  val runnersHostname = balancerConfig.getString("remote-hostname")
  val sbtRunnersPortsStart = balancerConfig.getInt("remote-sbt-ports-start")
  val containerType = balancerConfig.getString("snippets-storage")

  private val sbtRunnersPortsSize = balancerConfig.getInt("remote-sbt-ports-size")
  val sbtRunnersPortsEnd = sbtRunnersPortsStart + sbtRunnersPortsSize - 1
}

object ScastieConfig {
  def ofType(deploymentType: DeploymentType, deploymentFolder: File): ScastieConfig =
    deploymentType match {
      case Local => new ScastieConfig(deploymentFolder / "local.conf")
      case Staging => new ScastieConfig(deploymentFolder / "staging.conf")
      case Production => new ScastieConfig(deploymentFolder / "production.conf")
    }

  def logbackConfigPath(deploymentFolder: File): Path = (deploymentFolder / "logback.xml").toPath

}

class Deployment(
  rootFolder: File,
  version: String,
  sbtDockerImage: ImageName,
  metalsDockerImage: ImageName,
  deploymentType: DeploymentType,
  val logger: Logger
) {
  val deploymentFolder = rootFolder / "deployment"
  val config = ScastieConfig.ofType(deploymentType, deploymentFolder)

  val sbtDockerNamespace = sbtDockerImage.namespace.get
  val sbtDockerRepository = sbtDockerImage.repository

  val metalsDockerNamespace = metalsDockerImage.namespace.get
  val metalsDockerRepository = metalsDockerImage.repository

  def deploy(serverZip: Path) = {
    val time = LocalDateTime.now()
    val outputPath = deploymentFolder.toPath.resolve(s"generated-scripts-$time")

    if (!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }

    // the deployment will be sequential so if anything fails, other services will keep working.
    val success =
      createAndVerifyDeploymentScriptsData(outputPath) &&
      deployRunners() &&
      deployMetalsRunner() &&
      deployServer(serverZip)

    if (!success) logger.error("Deployment process stopped.")
  }

  /* Create runner script which will be used during deployment to start SBT runners docker containers */
  def createRunnersStartupScript(scriptOutputDirectory: Path): Path = {
    val fileName = if (deploymentType == Staging) "start-runners-staging.sh" else "start-runners.sh"
    val scriptPath = scriptOutputDirectory.resolve(fileName)

    val dockerImagePath = deploymentType match {
      case Local => s"$sbtDockerNamespace/$sbtDockerRepository:$gitHashNow"
      case Production => s"$sbtDockerNamespace/$sbtDockerRepository:latest"
      case Staging => s"$sbtDockerNamespace/$sbtDockerRepository:latest"
    }

    val containerName = if (deploymentType == Staging) "scastie-sbt-runner-staging" else "scastie-sbt-runner"

    val runnersStartupScriptContent: String =
      s"""#!/usr/bin/env bash
         |for port in `seq ${config.sbtRunnersPortsStart} ${config.sbtRunnersPortsEnd}`;
         |do
         |  echo "Starting Runner: Port $$port / ${config.sbtRunnersPortsEnd}"
         |  docker run \\
         |    --add-host jenkins.scala-sbt.org:127.0.0.1 \\
         |    --restart=always \\
         |    --name=$containerName-$$port \\
         |    --network=host \\
         |    -d \\
         |    -e RUNNER_PRODUCTION=true \\
         |    -e RUNNER_PORT=$$port \\
         |    -e SERVER_HOSTNAME=${config.serverHostname} \\
         |    -e SERVER_AKKA_PORT=${config.serverAkkaPort} \\
         |    -e RUNNER_HOSTNAME=${config.runnersHostname} \\
         |    $dockerImagePath
         |done""".stripMargin


    Files.write(scriptPath, runnersStartupScriptContent.getBytes())
    setPosixFilePermissions(scriptPath, executablePermissions)

    scriptPath
  }

  /* Create metals script which will be used during deployment to start metals docker container */
  def createMetalsStartupScript(scriptOutputDirectory: Path): Path = {
    val fileName = if (deploymentType == Staging) "start-metals-staging.sh" else "start-metals.sh"
    val scriptPath = scriptOutputDirectory.resolve(fileName)

    val dockerImagePath = deploymentType match {
      case Local => s"$metalsDockerNamespace/$metalsDockerRepository:$gitHashNow"
      case Production => s"$metalsDockerNamespace/$metalsDockerRepository:latest"
      case Staging => s"$metalsDockerNamespace/$metalsDockerRepository:latest"
    }

    val containerName = if (deploymentType == Staging) "scastie-metals-runner-staging" else "scastie-metals-runner"

    val metalsRunnerStartupScriptContent: String =
      s"""#!/usr/bin/env bash
         |echo "Starting Metals: Port ${config.metalsPort}"
         |docker run \\
         |  --restart=always \\
         |  --name=$containerName \\
         |  -p ${config.metalsPort}:${config.metalsPort} \\
         |  -d \\
         |  -e PORT=${config.metalsPort} \\
         |  -e CACHE_EXPIRE_IN_SECONDS=${config.cacheExpireInSeconds} \\
         |  $dockerImagePath""".stripMargin


    Files.write(scriptPath, metalsRunnerStartupScriptContent.getBytes())
    setPosixFilePermissions(scriptPath, executablePermissions)

    scriptPath
  }

  /* Compares script with its remote version */
  def compareScriptWithRemote(scriptPath: Path): Boolean = {
    val uri = s"${config.userName}@${config.runnersHostname}"
    val remoteScriptPath = scriptPath.getFileName().toString()
    val exitCode = Process(s"ssh $uri cat $remoteScriptPath") #| (s"diff - $scriptPath") ! logger
    logger.info(s"EXIT CODE $exitCode")
    exitCode == 0
  }

  def generateDeploymentScripts() = {
    val time = LocalDateTime.now()
    val outputPath = deploymentFolder.toPath.resolve(s"generated-scripts-$time")

    if (!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }

    createRunnersStartupScript(outputPath)
    createMetalsStartupScript(outputPath)
  }


  /*
   * Verifies if deployment scripts are up to date on remote
   * We don't want to automatically sync files between servers, as if it is misconfigured
   * all of the necessary files will copy instead of fail when they are not present.
   *
   * It doesn't require any user action unless a change in deployment has been made.
   * By manually copying the files, we are ensuring that everything is properly configured.
   */
  def createAndVerifyDeploymentScriptsData(scriptOutputDirectory: Path): Boolean = {
    val deploymentScript : Path = (deploymentFolder / "deploy.sh").toPath
    val runnerContainersStartupScript: Path = createRunnersStartupScript(scriptOutputDirectory)
    val metalsContainerStartupScript: Path = createMetalsStartupScript(scriptOutputDirectory)

    List(deploymentScript, runnerContainersStartupScript, metalsContainerStartupScript).map { script =>
      val isUpToDate: Boolean = compareScriptWithRemote(script)
      if (!isUpToDate) {
        val remoteScriptPath = script.getFileName().toString()
        logger.error(s"Deployment stopped. Script: $script is not up to date with remote version $remoteScriptPath or could not be validated. You have to update it manually. It should be located in the user home directory.")
      }
      isUpToDate
    }.forall(_ == true)
  }

  def deployRunners(): Boolean = {
    val uri = s"${config.userName}@${config.runnersHostname}"
    val exitCode = Process(s"ssh $uri ./deploy.sh SBT $deploymentType") ! logger
    exitCode == 0
  }

  def deployMetalsRunner(): Boolean = {
    val uri = s"${config.userName}@${config.runnersHostname}"
    val exitCode = Process(s"ssh $uri ./deploy.sh Metals $deploymentType") ! logger
    exitCode == 0
  }

  //#################################################################################################################//

  def deployLocal(serverZip: Path): Unit = {
    val destination = rootFolder.toPath.resolve("local")
    if (!Files.exists(destination)) {
      Files.createDirectory(destination)
    }

    val snippetsFolder = destination.resolve("snippets")
    if (!Files.exists(snippetsFolder)) {
      Files.createDirectory(snippetsFolder)
    }

    logger.info("Generate SBT runners startup script.")
    val runnerContainersStartupScript: Path = createRunnersStartupScript(destination)
    logger.info("Generate Metals runner startup script.")
    val metalsContainerStartupScript: Path = createMetalsStartupScript(destination)

    val deploymentFiles = deployServerFiles(serverZip, destination, local = true).files

    deploymentFiles.foreach(
      file =>
        Files
          .copy(file, destination.resolve(file.getFileName), REPLACE_EXISTING)
    )

    logger.success("Local deployment script are in ./local directory.")
  }

  def deployServer(serverZip: Path): Boolean = {
    val serverScriptDir = Files.createTempDirectory("server")

    val deploymentFiles =
      deployServerFiles(serverZip, serverScriptDir, local = false)

    deploymentFiles.files.foreach(rsyncServer)

    val scriptFileName = deploymentFiles.serverScript.getFileName
    val uri = config.userName + "@" + config.serverHostname
    val exitCode = Process(s"ssh $uri ./$scriptFileName") ! logger
    exitCode == 0
  }

  case class DeploymentFiles(
      serverZip: Path,
      serverScript: Path,
      productionConfig: Path,
      logbackConfig: Path,
      secretConfig: Option[Path] = None
  ) {
    def files: List[Path] = List(
      serverZip,
      serverScript,
      productionConfig,
      logbackConfig
    ) ++ secretConfig.toList
  }

  private def deployServerFiles(serverZip: Path, destination: Path, local: Boolean): DeploymentFiles = {
    logger.info("Generate server script")

    val serverScript = destination.resolve("server.sh")

    val configFileName = config.configurationFile.toPath().getFileName
    val logbackConfig = ScastieConfig.logbackConfigPath(deploymentFolder)
    val logbackConfigFileName= logbackConfig.getFileName()
    val serverZipFileName = serverZip.getFileName.toString.replace(".zip", "")

    val regex = "^[a-zA-Z0-9]+$".r
    if (regex.findFirstIn(config.userName).isEmpty)
      throw new IllegalStateException("Basedir contains space and may lead to removal of unwanted files.")

    val baseDir =
      if (!local) s"/home/${config.userName}/"
      else ""

    val isMongoDB = config.containerType == "mongo"
    val mongodbConfig = if (deploymentType == Production) "mongodb-prod.conf" else "mongodb-staging.conf"

    val content =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |
          |if [ -e ${baseDir}RUNNING_PID ]; then
          |  kill -9 `cat ${baseDir}RUNNING_PID`
          |fi
          |
          |if [ ! -f ${baseDir}${mongodbConfig} ] && ${isMongoDB}; then
          |  echo "mongodb configuration file: ${baseDir}${mongodbConfig} is missing"
          |  exit 1
          |fi
          |
          |rm -rf ${baseDir}server/*
          |unzip -o -d ${baseDir}server ${baseDir}$serverZipFileName
          |mv ${baseDir}server/$serverZipFileName/* ${baseDir}server/
          |rm -rf ${baseDir}server/$serverZipFileName
          |
          |nohup ${baseDir}server/bin/server \\
          |  -J-Xmx1G \\
          |  -Dconfig.file=${baseDir}${configFileName} \\
          |  -Dlogback.configurationFile=${baseDir}${logbackConfigFileName} \\
          |  &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, content.getBytes)
    setPosixFilePermissions(serverScript, executablePermissions)

    logger.info("Deploy servers")

    DeploymentFiles(
      serverZip,
      serverScript,
      config.configurationFile.toPath(),
      logbackConfig,
      None,
    )
  }


  private def rsync(file: Path, userName: String, hostname: String, logger: Logger): Unit = {
    val uri = userName + "@" + hostname
    val fileName = file.getFileName
    Process(s"rsync $file $uri:$fileName") ! logger
  }

  private def rsyncServer(file: Path) =
    rsync(file, config.userName, config.serverHostname, logger)

  private val executablePermissions = PosixFilePermissions.fromString("rwxr-xr-x")
  private def setPosixFilePermissions(path: Path, perms: java.util.Set[PosixFilePermission]): Path = {
    try Files.setPosixFilePermissions(path, perms)
    catch {
      case e: Exception => path
    }
  }
}
