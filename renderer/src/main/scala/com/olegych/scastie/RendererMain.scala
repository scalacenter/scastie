package com.olegych.scastie

import com.typesafe.config.ConfigFactory
import util.Properties

/**
  */
object RendererMain extends App {
  org.slf4j.bridge.SLF4JBridgeHandler.install()
  val loader = getClass.getClassLoader
  if (!Properties.propIsSet("config.resource")) System.setProperty("config.resource", "renderer")
  if (Properties.propIsSet("config.file")) System.clearProperty("config.resource")
  val config = ConfigFactory.load(loader)
  akka.actor.ActorSystem("actors", config, loader).awaitTermination()
}

