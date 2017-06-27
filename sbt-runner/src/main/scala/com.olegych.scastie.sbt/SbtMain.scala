package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory

object SbtMain {
  private val logger = LoggerFactory.getLogger("SbtMain")

  def main(args: Array[String]): Unit = {
    val pid = writeRunningPid()
    logger.info(s"Starting sbtRunner pid: $pid")

    val system = ActorSystem("SbtRemote")

    val config = ConfigFactory.load().getConfig("com.olegych.scastie.sbt")
    val isProduction = config.getBoolean("production")
    val timeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        config.getDuration("runTimeout", timeunit),
        timeunit
      )
    }

    logger.info(s" timeout: $timeout")
    logger.info(s" isProduction: $isProduction")

    system.actorOf(
      Props(
        new SbtActor(
          system = system,
          runTimeout = timeout,
          production = isProduction
        )
      ),
      name = "SbtActor"
    )

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
