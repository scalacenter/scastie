package scastie.server

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

object ServerConfig {
  val logger = Logger("ServerConfig")

  private val akkaRemoteConfig = ConfigFactory.load().getConfig("akka.remote.artery.canonical")

  logger.info("Akka TCP config")
  logger.info(akkaRemoteConfig.getString("hostname"))
  logger.info(akkaRemoteConfig.getInt("port").toString)

  val serverConfig = ConfigFactory.load().getConfig("com.olegych.scastie")
  val production   = serverConfig.getBoolean("production")
  val hostname     = serverConfig.getString("web.hostname")
  val port         = serverConfig.getInt("web.port")

  logger.info(s"Production: $production")
  logger.info(s"Server hostname: $hostname")
  logger.info(s"Server port: $port")
}

