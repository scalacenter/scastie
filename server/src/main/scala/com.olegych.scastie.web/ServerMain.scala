package com.olegych.scastie.web

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl._
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.olegych.scastie.balancer._
import com.olegych.scastie.util.ScastieFileUtil
import com.olegych.scastie.web.oauth2._
import com.olegych.scastie.web.routes._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import server.Directives._

object ServerMain {

  def main(args: Array[String]): Unit = {

    val logger = Logger("ServerMain")

    val port =
      if (args.isEmpty) 9000
      else args.head.toInt

    val config2 = ConfigFactory.load().getConfig("akka.remote.artery.canonical")
    logger.info("akka tcp config")
    logger.info(config2.getString("hostname"))
    logger.info(config2.getInt("port").toString)

    val config     = ConfigFactory.load().getConfig("com.olegych.scastie")
    val production = config.getBoolean("production")
    val hostname   = config.getString("web.hostname")

    logger.info(s"Production: $production")
    logger.info(s"Server hostname: $hostname")

    if (production) {
      ScastieFileUtil.writeRunningPid("RUNNING_PID")
    }

    implicit val system: ActorSystem = ActorSystem("Web")
    import system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val github         = new Github()
    val session        = new GithubUserSession(system)
    val userDirectives = new UserDirectives(session)

    val progressActor = system.actorOf(
      Props[ProgressActor](),
      name = "ProgressActor"
    )

    val statusActor = system.actorOf(
      StatusActor.props,
      name = "StatusActor"
    )

    val dispatchActor = system.actorOf(
      Props(new DispatchActor(progressActor, statusActor)),
      name = "DispatchActor"
    )

    val apiRoutes       = new ApiRoutes(dispatchActor, userDirectives).routes
    val progressRoutes  = new ProgressRoutes(progressActor).routes
    val downloadRoutes  = new DownloadRoutes(dispatchActor).routes
    val statusRoutes    = new StatusRoutes(statusActor, userDirectives).routes
    val scalaJsRoutes   = new ScalaJsRoutes(dispatchActor).routes
    val oauthRoutes     = new OAuth2Routes(github, session).routes
    val scalaLangRoutes = new ScalaLangRoutes(dispatchActor, userDirectives).routes
    val frontPageRoutes = new FrontPageRoutes(dispatchActor, production, hostname).routes

    val routes =
      oauthRoutes ~
      cors() {
        pathPrefix("api") {
          apiRoutes ~
            progressRoutes ~
            downloadRoutes ~
            statusRoutes ~
            scalaJsRoutes
        } ~
          scalaLangRoutes ~
          frontPageRoutes
      }

    val futureBinding = Http().newServerAt("localhost", port).bindFlow(routes)

    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }

    Await.result(system.whenTerminated, Duration.Inf)
  }

}
