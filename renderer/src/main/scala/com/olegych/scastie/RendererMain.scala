package com.olegych.scastie

import com.typesafe.config.ConfigFactory
import util.Properties

/**
  */
object RendererMain extends App {
  akka.actor.ActorSystem("application",
    ConfigFactory.load(getClass.getClassLoader, Properties.propOrElse("config.resource", "renderer")))
      .awaitTermination()
}
