package com.olegych.scastie

import com.typesafe.config.ConfigFactory
import util.Properties

/**
  */
object RendererMain extends App {
  val loader = getClass.getClassLoader
  val config = ConfigFactory.load(loader, Properties.propOrElse("config.resource", "renderer"))
  akka.actor.ActorSystem("actors", config, loader).awaitTermination()
}

