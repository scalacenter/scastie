package com.olegych.scastie

import akka.actor._
import com.typesafe.config.ConfigFactory

import org.slf4j.bridge.SLF4JBridgeHandler

import util.Properties

import java.io.{FileOutputStream, File}
import java.lang.management.ManagementFactory

object RendererMain {
  def main(args: Array[String]): Unit = {

    def writeRunningPid() {
      ManagementFactory.getRuntimeMXBean.getName
        .split('@')
        .headOption
        .map { pid =>
          val pidFile = new File(Properties.userDir, "RUNNING_PID")
          // The Logger is not initialized yet, we print the Process ID on STDOUT
          println("RendererMain process ID is " + pid)
          new FileOutputStream(pidFile).write(pid.getBytes)
          Runtime.getRuntime.addShutdownHook(new Thread {
            override def run {
              pidFile.delete()
            }
          })
        }
    }

    writeRunningPid()

    SLF4JBridgeHandler.install()

    val config = ConfigFactory.parseString(
      s"""|akka {
          |  actor {
          |    serialize-messages = on
          |    provider = "akka.remote.RemoteActorRefProvider"
          |  }
          |  remote {
          |    transport = "akka.remote.netty.NettyRemoteTransport"
          |    netty.tcp {
          |      hostname = "127.0.0.1"
          |      port = ${args.head}
          |    }
          |  }
          |  loggers = ["akka.event.slf4j.Slf4jLogger"]
          |}""".stripMargin
    )

    val system = ActorSystem("SbtRemote", config)

    // val remoteActor = system.actorOf(Props[RemoteActor], name = "RemoteActor")

    system.awaitTermination()
  }
}
