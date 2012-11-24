package com.olegych.scastie

import com.typesafe.config.ConfigFactory
import util.Properties

/**
  */
object RendererMain extends App {
  org.slf4j.bridge.SLF4JBridgeHandler.install()
  val loader = getClass.getClassLoader
  val config = ConfigFactory.load(loader, Properties.propOrElse("config.resource", "renderer"))
  akka.actor.ActorSystem("actors", config, loader).awaitTermination()
}

