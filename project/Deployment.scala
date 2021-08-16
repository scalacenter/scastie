import sbt._
import Keys._

import java.io.File
import java.nio.file._
import java.nio.file.attribute._
import com.typesafe.config.ConfigFactory
import sbtdocker.DockerKeys.{docker, imageNames}
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}
import com.typesafe.sslconfig.util.ConfigLoader.playConfigLoader
import sbtdocker.ImageName
import scala.collection.immutable.Seq
import scala.language.implicitConversions
import sys.process._

object Deployment {
  def settings(server: Project, sbtRunner: Project): Seq[Def.Setting[_]] = Seq(
    deployRunnersQuick := deployRunnersQuickTask(server, sbtRunner).value,
    deployServerQuick := deployServerQuickTask(server, sbtRunner).value,
    deployLocalQuick := deployLocalQuickTask(server, sbtRunner).value,
    dockerCompose := dockerComposeTask(server, sbtRunner).value,
  )

  lazy val deployRunnersQuick = taskKey[Unit]("Deploy sbt runners")
  lazy val deployServerQuick = taskKey[Unit]("Deploy server without building server zip")
  lazy val deployLocalQuick = taskKey[Unit]("Deploy locally")
  lazy val dockerCompose = taskKey[Unit](
    "Create docker-compose.yml (alternative way to deploy locally)"
  )

  /**
   *  @note The generated docker-compose.yml file contains all options to run scastie:
   *  - Don't mount secrets.conf and local.conf file to container
   *  - Don't set -Dsentry.dsn, -Dconfig.file `docker run` options
   */
  private def dockerComposeTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val log = streams.value.log
      val baseDir = (ThisBuild / baseDirectory).value

      Deployer.Local(baseDir, log).write("docker-compose.yml",
        s"""
          |# https://www.cloudsavvyit.com/10765/how-to-simplify-docker-compose-files-with-yaml-anchors-and-extensions/
          |x-runner: &runner
          |  image: ${ (sbtRunner / docker / imageNames).value.head }
          |  environment:
          |    JAVA_OPTS: >-
          |      -Dsentry.release=${ version.value }
          |      -Dcom.olegych.scastie.sbt.production=true
          |      -Dakka.cluster.seed-nodes.0=akka://sys@server:15000
          |      -Dakka.cluster.seed-nodes.1=akka://sys@sbt-runner-1:5150
          |  restart: unless-stopped
          |services:
          |  server:
          |    image: ${ (server / docker / imageNames).value.head }
          |    ports:
          |      - "9000:9000"
          |    environment:
          |      JAVA_OPTS: >-
          |        -Xmx1G
          |        -Dsentry.release=${ version.value }
          |        -Dcom.olegych.scastie.web.production=true
          |        -Dakka.remote.artery.canonical.hostname=server
          |        -Dakka.remote.artery.canonical.port=15000
          |        -Dakka.cluster.seed-nodes.0=akka://sys@server:15000
          |        -Dakka.cluster.seed-nodes.1=akka://sys@sbt-runner-1:5150
          |    volumes:
          |      # /app/data = value of DATA_DIR env variable defined in DockerHelper
          |      - ./target:/app/data
          |    restart: unless-stopped
          |  sbt-runner-1:
          |    <<: *runner
          |    command:
          |      - -Dakka.remote.artery.canonical.hostname=sbt-runner-1
          |      - -Dakka.remote.artery.canonical.port=5150
          |""".stripMargin)

      log.info("Created docker-compose.yml. `docker-compose up` to start scastie.")
    }

  /** @param rmi if true then delete all $label images
   *         rmi = false when `deployLocal`, true when `deploy` */
  private def dockerClean(label: String, rmi: Boolean = true): String = {
    val rmiComment = if (rmi) "# " else ""

    s"""|#!/bin/bash -x
        |
        |# kill and delete all $label containers
        |docker kill $$(docker ps -q -f label=$label)
        |
        |docker rm $$(docker ps -a -q -f label=$label)
        |
        |${rmiComment}docker rmi $$(docker images -q -f label=$label)
        |""".stripMargin
  }

  private def serverScript(
    image: ImageName,
    version: String,
    sentryDsn: String,
    c: DeployConf,
    mounts: Seq[String],
  ) =
    s"""|#!/bin/bash -x
        |
        |whoami
        |
        |docker run \\
        |  --name scastie-server \\
        |  --network=${ c.network } \\
        |  --publish ${ c.server.webPort }:${ c.server.webPort } \\
        |  --restart=always \\
        |  -d \\
        |  -v ${ mounts.mkString(" \\\n  -v ") } \\
        |  $image \\
        |    -J-Xmx1G \\
        |    -Dsentry.release=$version \\
        |    -Dsentry.dsn=$sentryDsn \\
        |    -Dakka.cluster.seed-nodes.0=${ c.server.akkaUri } \\
        |    -Dakka.cluster.seed-nodes.1=${ c.sbtRunners.firstNodeAkkaUri }
        |""".stripMargin

  // jenkins.scala-sbt.org points to 127.0.0.1 to workaround
  // https://github.com/sbt/sbt/issues/5458 and https://github.com/sbt/sbt/issues/5456
  private def runnersScript(image: ImageName, version: String, sentryDsn: String, c: DeployConf) = {
    import c.sbtRunners.{portsStart, portsEnd}
    val arteryHostnameOpts =
      if (c.network == "host")
        s"-Dakka.remote.artery.canonical.hostname=${ c.sbtRunners.host }"
      else """|-Dakka.remote.artery.canonical.hostname=scastie-runner-$idx \
        |      -Dakka.remote.artery.bind.hostname=0.0.0.0""".stripMargin

    s"""|#!/bin/bash -x
        |
        |whoami
        |
        |# Run all instances
        |for i in `seq $portsStart $portsEnd`;
        |do
        |  idx=$$(( i - $portsStart + 1 ))
        |  echo "Starting scastie-runner-$$idx at port $$i"
        |  docker run \\
        |    --add-host jenkins.scala-sbt.org:127.0.0.1 \\
        |    --name scastie-runner-$$idx \\
        |    --network=${ c.network } \\
        |    --restart=always \\
        |    -d \\
        |    $image \\
        |      -Dakka.remote.artery.canonical.port=$$i \\
        |      $arteryHostnameOpts \\
        |      -Dcom.olegych.scastie.sbt.production=true \\
        |      -Dsentry.release=$version \\
        |      -Dsentry.dsn=$sentryDsn \\
        |      -Dakka.cluster.seed-nodes.0=${ c.server.akkaUri } \\
        |      -Dakka.cluster.seed-nodes.1=${ c.sbtRunners.firstNodeAkkaUri }
        |done
        |""".stripMargin
  }

  import SecretsFile.sentryDsn
  private def deployLocalQuickTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val baseDir = (ThisBuild / baseDirectory).value
      val secretsFile = SecretsFile.local(baseDir)
      val configFile = baseDir / "deployment" / "local.conf"

      val deployConf = DeployConf(configFile)
      val deployer = Deployer.Local(baseDir / "local", streams.value.log)
      deployer.sync(
        configFile -> "application.conf",
        secretsFile -> "secrets.conf"
      )

      s"docker network ls -qf name=${ deployConf.network }".!! match {
        case "" => s"docker network create --driver bridge ${ deployConf.network }".!!
        case _  => // network created. Nothing to do
      }

      deployer.run("clean.sh", dockerClean("scastie"))

      deployer.run("server.sh",
        serverScript(
          (server / docker / imageNames).value.head,
          version.value,
          sentryDsn(secretsFile),
          deployConf,
          deployConf.server.mounts(deployer.rootDir.getAbsolutePath),
        )
      )

      deployer.run("sbt.sh",
        runnersScript(
          (sbtRunner / docker / imageNames).value.head,
          version.value,
          sentryDsn(secretsFile),
          deployConf
        )
      )
    }

  private def deployServerQuickTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val log = streams.value.log
      log.warn("deployServerQuick will not build and push the server docker image")

      val baseDir = (ThisBuild / baseDirectory).value
      val secretsFile = SecretsFile(baseDir)
      val configFile = baseDir / "deployment" / "production.conf"

      val deployConf = DeployConf(configFile)
      val deployer = Deployer.Remote(
        deployConf.server.host,
        deployConf.server.user,
        log,
        (server / target).value / "remote-deployer.tmp"
      )

      deployer.sync(
        configFile -> "application.conf",
        secretsFile -> "secrets.conf"
      )

      deployer.run("clean-server.sh", dockerClean("scastie=server"))
      deployer.run("server.sh",
        serverScript(
          (server / docker / imageNames).value.head,
          version.value,
          sentryDsn(secretsFile),
          deployConf,
          deployConf.server.mounts(deployer.home),
        )
      )
    }

  private def deployRunnersQuickTask(server: Project, sbtRunner: Project): Def.Initialize[Task[Unit]] =
    Def.task {
      val log = streams.value.log
      log.warn("deployServerQuick will not build and push the sbt-runner docker image")

      val baseDir = (ThisBuild / baseDirectory).value
      val secretsFile = SecretsFile(baseDir)
      val deployConf = DeployConf(baseDir / "deployment" / "production.conf")

      val deployer = Deployer.Remote(
        deployConf.server.host,
        deployConf.server.user,
        log,
        (server / target).value / "remote-deployer.tmp"
      )

      import deployConf.sbtRunners.{host, user}
      deployer.proxyRun(host, user, "clean-sbt.sh", dockerClean("scastie=runner"))
      deployer.proxyRun(host, user, "sbt.sh",
        runnersScript(
          (sbtRunner / docker / imageNames).value.head,
          version.value,
          sentryDsn(secretsFile),
          deployConf
        )
      )
    }

  private object SecretsFile {
    def sentryDsn(secretsFile: File): String =
      ConfigFactory
        .parseFile(secretsFile)
        .getString("com.olegych.scastie.sentry.dsn")

    def apply(baseDir: File): File = {
      val f = baseDir.getParentFile / "scastie-secrets" / "secrets.conf"
      if (! f.exists()) {
        Process(
          s"git clone git@github.com:scalacenter/scastie-secrets.git",
          cwd = baseDir.getParentFile
        ).!
      } else {
        // Please pull manually
        // Process("git pull origin master", cwd).!
      }
      f
    }

    def local(baseDir: File): File = {
      val f = baseDir / "secrets.conf"
      if (!f.exists()) {
        IO.write(f,
          """|# Please register at sentry.io
             |com.olegych.scastie.sentry.dsn="http://127.0.0.1"
             |""".stripMargin)
      }
      f
    }
  }

  import DeployConf._
  private case class DeployConf(
    network: String,
    sbtRunners: RunnerConf,
    server: ServerConf,
  )

  private object DeployConf {
    case class RunnerConf(user: String, host: String, portsStart: Int, portsSize: Int) {
      def portsEnd: Int = portsStart + portsSize - 1
      def firstNodeAkkaUri = s"akka://sys@$host:$portsStart"
    }
    object RunnerConf {
      implicit val loader: ConfigLoader[RunnerConf] = (c: EnrichedConfig) => RunnerConf(
        c.getOptional[String]("user").getOrElse("scastie"),
        c.get[String]("host"),
        c.get[Int]("ports-start"),
        c.get[Int]("ports-size"),
      )
    }

    case class ServerConf(user: String, host: String, port: Int, webPort: Int, dataMounts: Seq[String]) {
      def akkaUri = s"akka://sys@$host:$port"

      def mounts(workDir: String): Seq[String] = Seq(
        s"$workDir/application.conf:/app/conf/application.conf",
        s"$workDir/secrets.conf:/app/conf/secrets.conf",
      ) ++ dataMounts.map {
        case s if s.charAt(0) == '/' => s
        case s => s"$workDir/$s"
      }
    }
    object ServerConf {
      implicit val loader: ConfigLoader[ServerConf] = (c: EnrichedConfig) => ServerConf(
        c.getOptional[String]("user").getOrElse("scastie"),
        c.get[String]("host"),
        c.get[Int]("port"),
        c.getOptional[Int]("com.olegych.scastie.web.bind.port").getOrElse(9000),
        c.get[Seq[String]]("data-mounts"),
      )
    }

    implicit val loader: ConfigLoader[DeployConf] = (c: EnrichedConfig) => DeployConf(
      c.get[String]("network"),
      c.get[RunnerConf]("sbt-runners"),
      c.get[ServerConf]("server")
    )

    def apply(f: File): DeployConf = EnrichedConfig(
      ConfigFactory.parseFile(f).resolve()
    ).get[DeployConf]("com.olegych.scastie.deploy-config")

    implicit def toConfigLoader[A](f: EnrichedConfig => A): ConfigLoader[A] = playConfigLoader.map(f)
  }

  private sealed trait Deployer {
    def write(path: String, content: String, executable: Boolean = false): Unit
    def sync(f: File, newName: String): Unit
    def run(scriptName: String, scriptContent: String): Unit

    final def sync(sources: (File, String)*): Unit = sources.foreach {
      case (f, newName) => sync(f, newName)
    }

    protected def log: Logger
    protected def processLog(name: String): ProcessLogger = ProcessLogger(
      out => log.info(s"[$name] $out"),
      err => log.warn(s"[$name] $err")
    )
  }

  private object Deployer {
    case class Local(rootDir: File, log: Logger) extends Deployer {
      override def write(path: String, content: String, executable: Boolean = false): Unit = {
        val f = rootDir / path
        IO.write(f, content)
        if (executable) setExecutable(f)
      }

      override def sync(f: File, newName: String): Unit = {
        val newFile = rootDir / newName
        IO.createDirectory(newFile.getParentFile)
        IO.copyFile(f, newFile)
      }

      override def run(scriptName: String, scriptContent: String): Unit = {
        log.info(s"Generate $scriptName script")
        write(scriptName, scriptContent, executable = true)
        (rootDir / scriptName).absolutePath ! processLog(scriptName)
      }
    }

    case class Remote(host: String, user: String, log: Logger, tempFile: File) extends Deployer {
      val home = s"/home/$user"
      val uri: String = s"$user@$host"

      override def write(path: String, content: String, executable: Boolean = false): Unit = {
        IO.write(tempFile, content)
        if (executable) setExecutable(tempFile)
        sync(tempFile, path)
        IO.delete(tempFile)
      }

      override def sync(f: File, newName: String): Unit =
        s"rsync $f $uri:$newName" ! processLog("rsync")

      override def run(scriptName: String, scriptContent: String): Unit = {
        log.info(s"Generate $scriptName script")
        write(scriptName, scriptContent, executable = true)
        s"ssh $uri ./$scriptName" ! processLog(scriptName)
      }

      /** run `scriptContent` on `remoteHost` by:
       *  + ssh to this `host`
       *  + then, from this `host`, ssh to `remoteHost` and run */
      def proxyRun(remoteHost: String, remoteUser: String, scriptName: String, scriptContent: String): Unit = {
        log.info(s"Generate $scriptName script")
        write(scriptName, scriptContent, executable = true)

        val remoteUri = s"$remoteUser@$remoteHost"
        run(s"proxy-$scriptName",
          s"""|rsync $scriptName $remoteUri:$scriptName
              |ssh $remoteUri ./$scriptName
              |rm $scriptName
              |""".stripMargin)
      }
    }

    private val executablePermissions = PosixFilePermissions.fromString("rwxr-xr-x")

    private def setExecutable(f: File): File =
      try Files.setPosixFilePermissions(f.toPath, executablePermissions).toFile
      catch { case _: Exception => f }
  }
}
