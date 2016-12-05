package com.olegych.scastie
package sbt

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import util.Properties

import java.nio.file._

object SbtMain {
  def writeRunningPid() {
    java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split('@').headOption.foreach { pid =>
      val pidFile = Paths.get(Properties.userDir, "RUNNING_PID")
      println("Play server process ID is " + pid)
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

    val config = ConfigFactory.parseString(
      s"""|akka {
          |  actor {
          |    provider = "akka.remote.RemoteActorRefProvider"
          |   }
          |   remote {
          |     transport = "akka.remote.netty.NettyRemoteTransport"
          |     netty.tcp {
          |       hostname = "127.0.0.1"
          |       port = 5150
          |     }
          |   }
          |}""".stripMargin)

    val system = ActorSystem("SbtRemote", config)
    val remoteActor = system.actorOf(Props[SbtActor], name = "SbtActor")
    system.awaitTermination()
  }
}
