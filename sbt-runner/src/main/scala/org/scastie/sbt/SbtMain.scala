package org.scastie.sbt

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Await

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.scastie.util.ReconnectInfo
import org.scastie.util.ScastieFileUtil.writeRunningPid
import org.slf4j.LoggerFactory

object SbtMain {

  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("SbtRunner")

    val config2 = ConfigFactory.load().getConfig("akka.remote.artery.canonical")
    logger.info("akka tcp config")
    logger.info("  '" + config2.getString("hostname") + "'")
    logger.info("  " + config2.getInt("port"))

    val config = ConfigFactory.load().getConfig("org.scastie")

    val serverConfig = config.getConfig("web")
    val sbtConfig = config.getConfig("sbt")
    val sharedRunnersConfig = config.getConfig("runners")

    val isProduction = sbtConfig.getBoolean("production")

    val isReconnecting = sbtConfig.getBoolean("reconnect")

    if (isProduction) {
      val pid = writeRunningPid("RUNNING_PID")
      logger.info(s"Starting sbtRunner pid: $pid")
    }

    val runTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sharedRunnersConfig.getDuration("runTimeout", timeunit),
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

    val reconnectInfo = ReconnectInfo(
      serverHostname = serverConfig.getString("hostname"),
      serverAkkaPort = serverConfig.getInt("akka-port"),
      actorHostname = sbtConfig.getString("hostname"),
      actorAkkaPort = sbtConfig.getInt("akka-port")
    )

    logger.info("  runTimeout: {}", runTimeout)
    logger.info("  reloadTimeout: {}", reloadTimeout)
    logger.info("  isProduction: {}", isProduction)
    logger.info("  runner hostname: {}", reconnectInfo.actorHostname)
    logger.info("  runner port: {}", reconnectInfo.actorAkkaPort)
    logger.info("  server hostname: {}", reconnectInfo.serverHostname)
    logger.info("  server port: {}", reconnectInfo.serverAkkaPort)

    system.actorOf(
      Props(
        new SbtActor(
          system = system,
          runTimeout = runTimeout,
          reloadTimeout = reloadTimeout,
          isProduction = isProduction,
          readyRef = None,
          reconnectInfo = Some(reconnectInfo)
        )
      ),
      name = "SbtActor"
    )

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }

}
