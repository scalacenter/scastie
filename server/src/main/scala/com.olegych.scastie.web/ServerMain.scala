package com.olegych.scastie.web

import com.olegych.scastie.web.routes._
import com.olegych.scastie.web.oauth2._
import com.olegych.scastie.balancer._
import com.olegych.scastie.util.ScastieFileUtil

import akka.http.scaladsl._
import server.Directives._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object ServerMain {
  def main(args: Array[String]): Unit = {

    val logger = Logger("ServerMain")

    val port =
      if (args.isEmpty) 9000
      else args.head.toInt

    val config2 = ConfigFactory.load().getConfig("akka.remote.artery.canonical")
    println("akka tcp config")
    println(config2.getString("hostname"))
    println(config2.getInt("port"))

    val config = ConfigFactory.load().getConfig("com.olegych.scastie.web")
    val production = config.getBoolean("production")

    if (production) {
      ScastieFileUtil.writeRunningPid("RUNNING_PID")
    }

    implicit val system: ActorSystem = ActorSystem("Web")
    import system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val github = new Github
    val session = new GithubUserSession(system)
    val userDirectives = new UserDirectives(session)

    val progressActor =
      system.actorOf(
        Props[ProgressActor](),
        name = "ProgressActor"
      )

    val statusActor =
      system.actorOf(
        StatusActor.props,
        name = "StatusActor"
      )

    val dispatchActor =
      system.actorOf(
        Props(new DispatchActor(progressActor, statusActor)),
        name = "DispatchActor"
      )

    val routes = concat(
      cors()(
        pathPrefix("api")(
          concat(
            new ApiRoutes(dispatchActor, userDirectives).routes,
            new ProgressRoutes(progressActor).routes,
            new DownloadRoutes(dispatchActor).routes,
            new StatusRoutes(statusActor, userDirectives).routes,
            new ScalaJsRoutes(dispatchActor).routes
          )
        )
      ),
      new OAuth2Routes(github, session).routes,
      cors()(
        concat(
          new ScalaLangRoutes(dispatchActor, userDirectives).routes,
          new FrontPageRoutes(dispatchActor, production).routes
        )
      )
    )

    Await.result(Http().newServerAt("0.0.0.0", port).bindFlow(routes), 1.seconds)
    logger.info(s"Scastie started (port: $port)")

//    scala.io.StdIn.readLine("press enter to stop server")
//    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
