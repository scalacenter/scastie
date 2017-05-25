package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

import java.util.concurrent.TimeUnit

object SbtMain {
  def main(args: Array[String]): Unit = {
    writeRunningPid()

    val system = ActorSystem("SbtRemote")

    val config = ConfigFactory.load().getConfig("com.olegych.scastie.sbt")
    val timeout = {
      val timeunit = TimeUnit.SECONDS
      FiniteDuration(
        config.getDuration("runTimeout", timeunit),
        timeunit
      )
    }

    system.actorOf(
      Props(
        new SbtActor(
          system = system,
          runTimeout = timeout,
          production = config.getBoolean("production")
        )
      ),
      name = "SbtActor"
    )

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
