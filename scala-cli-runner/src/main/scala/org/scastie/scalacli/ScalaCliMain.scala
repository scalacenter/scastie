package org.scastie.scalacli

import org.scastie.util.ScastieFileUtil.writeRunningPid
import org.scastie.util.ReconnectInfo

import org.apache.pekko.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory

/**
  * This object provides the main endpoint for the Scala-CLI runner.
  * Its role is to create and setup the ActorSystem and create the ScalaCli Actor
  */
object ScalaCliMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("ScalaCliRunner")

    assert(scala.sys.process.Process("which scala-cli").! == 0, "scala-cli is not installed")

    val config2 = ConfigFactory.load().getConfig("pekko.remote.artery.canonical")
    logger.info("remote tcp config")
    logger.info("  '" + config2.getString("hostname") + "'")
    logger.info("  " + config2.getInt("port"))

    val config = ConfigFactory.load().getConfig("org.scastie")

    val serverConfig = config.getConfig("web")
    val scalaCliConfig = config.getConfig("scala-cli")
    val sharedRunnersConfig = config.getConfig("runners")

    val isProduction = scalaCliConfig.getBoolean("production")
    val isReconnecting = scalaCliConfig.getBoolean("reconnect")

    if (isProduction) {
      val pid = writeRunningPid("RUNNING_PID")
      logger.info(s"Starting scala-cli runner pid: $pid")
    }

    val runTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sharedRunnersConfig.getDuration("runTimeout", timeunit),
        timeunit
      )
    }

    val compilationTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sharedRunnersConfig.getDuration("compilationTimeout", timeunit),
        timeunit
      )
    }

    val reloadTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sharedRunnersConfig.getDuration("reloadTimeout", timeunit),
        timeunit
      )
    }

    // Reconnect info
    val reconnectInfo = ReconnectInfo(
      serverHostname = serverConfig.getString("hostname"),
      serverRemotePort = serverConfig.getInt("remote-port"),
      actorHostname = scalaCliConfig.getString("hostname"),
      actorRemotePort = scalaCliConfig.getInt("remote-port")
    )

    system.actorOf(
      Props(
        new ScalaCliActor(
          isProduction = isProduction,
          runTimeout = runTimeout,
          compilationTimeout = compilationTimeout,
          reloadTimeout = reloadTimeout,
          reconnectInfo = Some(reconnectInfo)
        )
      ),
      name = "ScalaCliActor"
    )

    logger.info("  runTimeout: {}", runTimeout)
    logger.info("  compilationTimeout: {}", compilationTimeout)
    logger.info("  isProduction: {}", isProduction)
    logger.info("  runner hostname: {}", reconnectInfo.actorHostname)
    logger.info("  runner port: {}", reconnectInfo.actorRemotePort)
    logger.info("  server hostname: {}", reconnectInfo.serverHostname)
    logger.info("  server port: {}", reconnectInfo.serverRemotePort)

    logger.info("ScalaCliActor started")

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
