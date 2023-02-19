package `com.olegych.scastie.sclirunner`

import com.olegych.scastie.util.ScastieFileUtil.writeRunningPid
import com.olegych.scastie.util.ReconnectInfo

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory

object SbtMain {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val system = ActorSystem("ScliRunner")

    val config2 = ConfigFactory.load().getConfig("akka.remote.artery.canonical")
    logger.info("akka tcp config")
    logger.info("  '" + config2.getString("hostname") + "'")
    logger.info("  " + config2.getInt("port"))

    val config = ConfigFactory.load().getConfig("com.olegych.scastie")

    val serverConfig = config.getConfig("web")

    val isProduction = true

    // TODO: check if production
    // Create appropriate config files
    if (isProduction) {
      val pid = writeRunningPid("RUNNING_PID")
      logger.info(s"Starting scliRunner pid: $pid")
    }

    system.actorOf(
      Props(
        new ScliActor(
          system = system,
          isProduction = isProduction,
          readyRef = None
        )
      ),
      name = "ScliActor"
    )

    logger.info("ScliRunner + ScliActor started")

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
