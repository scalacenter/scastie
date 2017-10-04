package com.olegych.scastie.ensime

import com.olegych.scastie.util.ScastieFileUtil.writeRunningPid
import com.olegych.scastie.util.ReconnectInfo

import akka.actor.{ActorSystem, Props}

import com.typesafe.config.ConfigFactory

import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
import java.util.concurrent.TimeUnit

object EnsimeMain {
  def main(args: Array[String]): Unit = {
    val log = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("EnsimeRunner")

    val serverConfig = ConfigFactory.load().getConfig("com.olegych.scastie.web")
    val ensimeConfig =
      ConfigFactory.load().getConfig("com.olegych.scastie.ensime")

    val isProduction = ensimeConfig.getBoolean("production")

    if (isProduction) {
      val pid = writeRunningPid()
      log.info(s"Starting ensimeRunner pid: $pid")
    }

    val sbtReloadTimeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        ensimeConfig.getDuration("sbtReloadTimeout", timeunit),
        timeunit
      )
    }

    val reconnectInfo =
      ReconnectInfo(
        serverHostname = serverConfig.getString("hostname"),
        serverAkkaPort = serverConfig.getInt("akka-port"),
        actorHostname = ensimeConfig.getString("hostname"),
        actorAkkaPort = ensimeConfig.getInt("akka-port")
      )

    log.info("  sbtReloadTimeout: {}", sbtReloadTimeout)
    log.info("  isProduction: {}", isProduction)
    log.info("  runner hostname: {}", reconnectInfo.actorHostname)
    log.info("  runner port: {}", reconnectInfo.actorAkkaPort)
    log.info("  server hostname: {}", reconnectInfo.serverHostname)
    log.info("  server port: {}", reconnectInfo.serverAkkaPort)

    system.actorOf(
      Props(
        new EnsimeActor(
          system = system,
          sbtReloadTimeout = sbtReloadTimeout,
          reconnectInfo = Some(reconnectInfo)
        )
      ),
      name = "EnsimeActor"
    )

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
