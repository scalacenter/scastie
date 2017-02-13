package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}

import scala.concurrent.Await
import scala.concurrent.duration._

object SbtMain { 
  def main(args: Array[String]): Unit = {
    writeRunningPid()

    val system = ActorSystem("SbtRemote")

    val remoteActor =
      system.actorOf(Props(new SbtActor(10.seconds, production = true)),
                     name = "SbtActor")
    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
