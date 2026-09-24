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
  def settings(server: Project, sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project): Seq[Def.Setting[Task[Unit]]] = Seq(
    deploy := deployTask(server = server, sbtRunner = sbtRunner,  scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner, deploymentType = Production).value,
    deployStaging := deployTask(server = server, sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner, deploymentType = Staging).value,
    publishContainers := publishContainers(sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner).value,
    generateDeploymentScripts := generateDeploymentScriptsTask(server = server, sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner).value,
    deployLocal := deployLocalTask(server = server, sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner).value
  )

  lazy val deploy = taskKey[Unit]("Deploy server and sbt instances")
  lazy val deployStaging = taskKey[Unit]("Deploy server and sbt instances with staging configuration")
  lazy val publishContainers = taskKey[Unit]("Publishes sbt runners and metals runner to docker repository")
  lazy val generateDeploymentScripts = taskKey[Unit]("Generates deployment scripts with production configuration.")
  lazy val deployLocal = taskKey[Unit]("Deploy locally")

  def deployTask(server: Project, sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project, deploymentType: DeploymentType): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner, deploymentType = deploymentType).value
      val serverZip = (server / Universal / packageBin).value.toPath

      deployment.deploy(serverZip)
    }

  def publishContainers(sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      (sbtRunner / dockerBuildAndPush).value
      (scalaCliRunner / dockerBuildAndPush).value
      (metalsRunner / dockerBuildAndPush).value
    }

  def generateDeploymentScriptsTask(server: Project, sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner, deploymentType = Production).value
      deployment.generateDeploymentScripts()
    }


  def deployLocalTask(server: Project, sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val deployment = deploymentTask(sbtRunner = sbtRunner, scalaCliRunner = scalaCliRunner, metalsRunner = metalsRunner, deploymentType = Local).value
      val serverZip = (server / Universal / packageBin).value.toPath
      (sbtRunner / docker).value
      (scalaCliRunner / docker).value
      (metalsRunner / docker).value

      deployment.deployLocal(serverZip)
    }

  private def deploymentTask(sbtRunner: Project, scalaCliRunner: Project, metalsRunner: Project, deploymentType: DeploymentType): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (ThisBuild / baseDirectory).value,
        version = version.value,
        sbtDockerImage = (sbtRunner / docker / imageNames).value.head,
        scalaCliDockerImage = (scalaCliRunner / docker / imageNames).value.head,
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

trait RemoteService
case object SBT extends RemoteService {
  override def toString(): String = "SBT"
}
case object ScalaCLI extends RemoteService {
  override def toString(): String = "Scala-CLI"
}
case object Metals extends RemoteService {
  override def toString(): String = "Metals"
}

class ScastieConfig(val configurationFile: File) {
  val config = ConfigFactory.parseFile(configurationFile)
  val userName = "scastie"

  val serverConfig = config.getConfig("org.scastie.web")
  val serverHostname = serverConfig.getString("hostname")
  val serverAkkaPort = serverConfig.getInt("akka-port")

  val metalsConfig = config.getConfig("org.scastie.metals")
  val metalsPort = metalsConfig.getInt("port")
  val cacheExpireInSeconds = metalsConfig.getInt("cache-expire-in-seconds")

  val balancerConfig = config.getConfig("org.scastie.balancer")
  val runnersHostname = balancerConfig.getString("remote-hostname")
  val sbtRunnersPortsStart = balancerConfig.getInt("remote-sbt-ports-start")
  val scalaCliRunnersPortsStart = balancerConfig.getInt("remote-scli-ports-start")
  val containerType = balancerConfig.getString("snippets-storage")

  private val sbtRunnersPortsSize = balancerConfig.getInt("remote-sbt-ports-size")
  val sbtRunnersPortsEnd = sbtRunnersPortsStart + sbtRunnersPortsSize - 1
  private val scalaCliRunnersPortsSize = balancerConfig.getInt("remote-scli-ports-size")
  val scalaCliRunnersPortsEnd = scalaCliRunnersPortsStart + scalaCliRunnersPortsSize - 1
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
  scalaCliDockerImage: ImageName,
  metalsDockerImage: ImageName,
  deploymentType: DeploymentType,
  val logger: Logger
) {
  val deploymentFolder = rootFolder / "deployment"
  val config = ScastieConfig.ofType(deploymentType, deploymentFolder)

  val metalsDockerNamespace = metalsDockerImage.namespace.get
  val metalsDockerRepository = metalsDockerImage.repository

  val scalaCliContainerName = "scastie-scala-cli-runner"
  val sbtContainerName = "scastie-sbt-runner"

  val sharedCacheName = "scastie-cache"
  def sharedCacheFlag(directory: String) = s"-v $sharedCacheName:${sharedCacheDirectory(directory)}"
  def sharedCacheDirectory(directory: String) = s"/$sharedCacheName/$directory"

  def deploy(serverZip: Path) = {
    val time = LocalDateTime.now()
    val outputPath = deploymentFolder.toPath.resolve(s"generated-scripts-$time")

    if (!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }

    // the deployment will be sequential so if anything fails, other services will keep working.
    val success =
      createAndVerifyDeploymentScriptsData(outputPath) &&
      deployService(SBT) &&
      deployService(ScalaCLI) &&
      deployService(Metals) &&
      deployServer(serverZip)

    if (!success) logger.error("Deployment process stopped.")
  }

  /* Create runner script which will be used during deployment to start SBT runners docker containers */
  def createRunnersStartupScript(scriptOutputDirectory: Path, dockerImage: ImageName, startPort: Int, endPort: Int, containerName: String): Path = {
    val fileName = if (deploymentType == Staging) s"start-${containerName}s-staging.sh" else s"start-${containerName}s.sh"
    val scriptPath = scriptOutputDirectory.resolve(fileName)

    val imageNamespace = dockerImage.namespace.get
    val imageRepository = dockerImage.repository

    val dockerImagePath = deploymentType match {
      case Local => s"$imageNamespace/$imageRepository:$gitHashNow"
      case Production => s"$imageNamespace/$imageRepository:latest"
      case Staging => s"$imageNamespace/$imageRepository:latest"
    }

    val containerName0 = if (deploymentType == Staging) s"$containerName-staging" else containerName

    val runnersStartupScriptContent: String =
      s"""#!/usr/bin/env bash
         |docker volume create ${sharedCacheName} || true
         |
         |for port in `seq $startPort $endPort`;
         |do
         |  echo "Starting Runner: Port $$port / $endPort (with shared cache)"
         |  docker run \\
         |    --add-host jenkins.scala-sbt.org:127.0.0.1 \\
         |    --restart=always \\
         |    --name=${containerName0}-$$port \\
         |    --network=host \\
         |    ${sharedCacheFlag("coursier")} \\
         |    -d \\
         |    -e RUNNER_PRODUCTION=true \\
         |    -e RUNNER_PORT=$$port \\
         |    -e SERVER_HOSTNAME=${config.serverHostname} \\
         |    -e SERVER_AKKA_PORT=${config.serverAkkaPort} \\
         |    -e RUNNER_HOSTNAME=${config.runnersHostname} \\
         |    -e COURSIER_CACHE=${sharedCacheDirectory("coursier")} \\
         |    $dockerImagePath
         |done
         |""".stripMargin


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
         |echo "Starting Metals: Port ${config.metalsPort} (with shared cache)"
         |docker volume create ${sharedCacheName} || true
         |
         |docker run \\
         |  --restart=always \\
         |  --name=$containerName \\
         |  -p ${config.metalsPort}:${config.metalsPort} \\
         |  ${sharedCacheFlag("coursier")} \\
         |  -d \\
         |  -e COURSIER_CACHE=${sharedCacheDirectory("coursier")} \\
         |  -e PORT=${config.metalsPort} \\
         |  -e CACHE_EXPIRE_IN_SECONDS=${config.cacheExpireInSeconds} \\
         |  -e IS_DOCKER=true \\
         |  $dockerImagePath
         |""".stripMargin

    Files.write(scriptPath, metalsRunnerStartupScriptContent.getBytes())
    setPosixFilePermissions(scriptPath, executablePermissions)

    scriptPath
  }

  /* Compares script with its remote version */
  def compareScriptWithRemote(scriptPath: Path): Boolean = {
    val uri = s"${config.userName}@${config.runnersHostname}"
    val remoteScriptPath = scriptPath.getFileName().toString()
    val exitCode = Process(s"ssh $uri cat $remoteScriptPath") #| (s"diff -B - $scriptPath") ! logger
    logger.info(s"EXIT CODE $exitCode")
    exitCode == 0
  }

  def generateDeploymentScripts() = {
    val time = LocalDateTime.now()
    val outputPath = deploymentFolder.toPath.resolve(s"generated-scripts-$time")

    if (!Files.exists(outputPath)) {
      Files.createDirectory(outputPath)
    }

    createRunnersStartupScript(outputPath, sbtDockerImage, config.sbtRunnersPortsStart, config.sbtRunnersPortsEnd, sbtContainerName)
    createRunnersStartupScript(outputPath, scalaCliDockerImage, config.scalaCliRunnersPortsStart, config.scalaCliRunnersPortsEnd, scalaCliContainerName)
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
    val sbtRunnerContainersStartupScript = createRunnersStartupScript(
      scriptOutputDirectory, sbtDockerImage, config.sbtRunnersPortsStart, config.sbtRunnersPortsEnd, sbtContainerName
    )
    val scalaCliRunnerContainersStartupScript = createRunnersStartupScript(
      scriptOutputDirectory, scalaCliDockerImage, config.scalaCliRunnersPortsStart, config.scalaCliRunnersPortsEnd, scalaCliContainerName
    )
    val metalsContainerStartupScript: Path = createMetalsStartupScript(scriptOutputDirectory)

    List(deploymentScript, sbtRunnerContainersStartupScript, scalaCliRunnerContainersStartupScript, metalsContainerStartupScript).map { script =>
      val isUpToDate: Boolean = compareScriptWithRemote(script)
      if (!isUpToDate) {
        val remoteScriptPath = script.getFileName().toString()
        logger.error(s"Deployment stopped. Script: $script is not up to date with remote version $remoteScriptPath or could not be validated. You have to update it manually. It should be located in the user home directory.")
      }
      isUpToDate
    }.forall(_ == true)
  }

  def deployService(service: RemoteService): Boolean = {
    val uri = s"${config.userName}@${config.runnersHostname}"
    val exitCode = Process(s"ssh $uri ./deploy.sh $service $deploymentType") ! logger
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
    val sbtRunnerContainersStartupScript = createRunnersStartupScript(
      destination, sbtDockerImage, config.sbtRunnersPortsStart, config.sbtRunnersPortsEnd, sbtContainerName
    )
    logger.info("Generate Scala-CLI runners startup script.")
    val scalaCliRunnerContainersStartupScript = createRunnersStartupScript(
      destination, scalaCliDockerImage, config.scalaCliRunnersPortsStart, config.scalaCliRunnersPortsEnd, scalaCliContainerName
    )
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

    val isPostgres = config.containerType == "postgres"
    val postgresConfig = if (deploymentType == Production) "postgres-prod.conf" else "postgres-staging.conf"

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
          |if [ ! -f ${baseDir}${postgresConfig} ] && ${isPostgres}; then
          |  echo "postgres configuration file: ${baseDir}${postgresConfig} is missing"
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
