package com.olegych.scastie.sbt

import akka.actor.typed.scaladsl.Behaviors
import com.olegych.scastie.util.ShowConfig
import com.typesafe.sslconfig.util.EnrichedConfig
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

object SbtMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val config = EnrichedConfig(
      ConfigFactory.load().getConfig("com.olegych.scastie")
    )
    val sbtConf = config.get[SbtConf]("sbt")

    val system = ActorSystem[Nothing](
      Guardian(sbtConf),
      config.get[String]("system-name")
    )

    logger.info(ShowConfig(system.settings.config,
      """|# Scastie sbt runner started with config:
         |akka.remote.artery {
         |  canonical
         |  bind
         |}
         |com.olegych.scastie.sbt
         |""".stripMargin))

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

private object Guardian {
  def apply(conf: SbtConf): Behavior[Nothing] =
    Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(SbtActor(conf), "SbtActor")
      Behaviors.empty
    }
}
