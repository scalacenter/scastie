package com.olegych.scastie

import com.typesafe.config.ConfigFactory
import util.Properties
import java.io.{FileOutputStream, File}

/**
  */
object RendererMain extends App {
  def writeRunningPid() {
    java.lang.management.ManagementFactory.getRuntimeMXBean.getName.split('@').headOption.map { pid =>
      val pidFile = new File(Properties.userDir, "RUNNING_PID")
      // The Logger is not initialized yet, we print the Process ID on STDOUT
      println("Play server process ID is " + pid)
      new FileOutputStream(pidFile).write(pid.getBytes)
      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run {
          pidFile.delete()
        }
      })
    }
  }
  org.slf4j.bridge.SLF4JBridgeHandler.install()
  val loader = getClass.getClassLoader
  if (!Properties.propIsSet("config.resource")) System.setProperty("config.resource", "renderer.conf")
  if (Properties.propIsSet("config.file")) System.clearProperty("config.resource")
  writeRunningPid()
  val config = ConfigFactory.load(loader)
  akka.actor.ActorSystem("actors", config, loader).awaitTermination()
}

