package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory

object SbtMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("SbtRemote")

    val config = ConfigFactory.load().getConfig("com.olegych.scastie")

    val serverConfig = config.getConfig("web")
    val sbtConfig = config.getConfig("sbt")

    val isProduction = sbtConfig.getBoolean("production")

    if (isProduction) {
      val pid = writeRunningPid()
      logger.info(s"Starting sbtRunner pid: $pid")
    }

    val timeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        sbtConfig.getDuration("runTimeout", timeunit),
        timeunit
      )
    }

    logger.info(s" timeout: $timeout")
    logger.info(s" isProduction: $isProduction")

    val reconnectInfo =
      ReconnectInfo(
        serverHostname = serverConfig.getString("hostname"),
        serverAkkaPort = serverConfig.getInt("akka-port"),
        runnerHostname = sbtConfig.getString("hostname"),
        runnerAkkaPort = sbtConfig.getInt("akka-port")
      )

    system.actorOf(
      Props(
        new SbtActor(
          system = system,
          runTimeout = timeout,
          production = isProduction,
          withEnsime = false,
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
