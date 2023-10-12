package org.scastie.scalacli

import org.scastie.util.ScastieFileUtil.writeRunningPid
import org.scastie.util.ReconnectInfo

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory

/**
  * This object provides the main endpoint for the Scala-CLI runner.
  * Its role is to create and setup the ActorSystem and create the ScalaCli Actor
  */
object SbtMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("ScliRunner")

    val config2 = ConfigFactory.load().getConfig("akka.remote.artery.canonical")
    logger.info("akka tcp config")
    logger.info("  '" + config2.getString("hostname") + "'")
    logger.info("  " + config2.getInt("port"))

    val config = ConfigFactory.load().getConfig("org.scastie")

    val serverConfig = config.getConfig("web")
    val sbtConfig = config.getConfig("sbt")

    val isProduction = true

    // TODO: check if production
    // Create appropriate config files
    if (isProduction) {
      val pid = writeRunningPid("RUNNING_PID")
      logger.info(s"Starting scliRunner pid: $pid")
    }

    val runTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sbtConfig.getDuration("runTimeout", timeunit),
        timeunit
      )
    }

    // Reconnect info
    val reconnectInfo = ReconnectInfo(
      serverHostname = serverConfig.getString("hostname"),
      serverAkkaPort = serverConfig.getInt("akka-port"),
      actorHostname = sbtConfig.getString("hostname"),
      actorAkkaPort = sbtConfig.getInt("akka-port")
    )

    system.actorOf(
      Props(
        new ScliActor(
          system = system,
          isProduction = isProduction,
          readyRef = None,
          runTimeout = runTimeout,
          reconnectInfo = Some(reconnectInfo)
        )
      ),
      name = "ScliActor"
    )

    logger.info("ScliActor started")

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
