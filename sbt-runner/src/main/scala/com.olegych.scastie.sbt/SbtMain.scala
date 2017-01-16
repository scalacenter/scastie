package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}

import scala.concurrent.duration._

import util.Properties
import java.nio.file._

object SbtMain {
  def writeRunningPid() {
    java.lang.management.ManagementFactory.getRuntimeMXBean.getName
      .split('@')
      .headOption
      .foreach { pid =>
        val pidFile = Paths.get(Properties.userDir, "RUNNING_PID")
        println(s"Runner PID: $pid")
        Files.write(pidFile, pid.getBytes)
        Runtime.getRuntime.addShutdownHook(new Thread {
          override def run: Unit = {
            Files.delete(pidFile)
            ()
          }
        })
      }
  }

  def main(args: Array[String]): Unit = {
    writeRunningPid()

    val system      = ActorSystem("SbtRemote")
    val remoteActor = system.actorOf(Props(new SbtActor(40.seconds, new Sbt())), name = "SbtActor")
    system.awaitTermination()
  }
}
