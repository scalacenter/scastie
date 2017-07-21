package com.olegych.scastie
package web

import routes._
import oauth2._
import balancer._

import akka.http.scaladsl._
import server._

import server.Directives._

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

    val config = ConfigFactory.load().getConfig("com.olegych.scastie.web")
    val production = config.getBoolean("production")

    if (production) {
      writeRunningPid()
    }

    implicit val system = ActorSystem("scastie")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github
    val session = new GithubUserSession
    val userDirectives = new UserDirectives(session)

    val progressActor =
      system.actorOf(
        Props[ProgressActor],
        name = "ProgressActor"
      )

    val statusActor =
      system.actorOf(
        Props[StatusActor],
        name = "StatusActor"
      )

    val dispatchActor =
      system.actorOf(
        Props(new DispatchActor(progressActor, statusActor)),
        name = "DispatchActor"
      )

    statusActor ! SetDispatcher(dispatchActor)

    val userFacingRoutes =
      new FrontPageRoutes(production).routes

    val programmaticRoutes =
      concat(
        DebugRoutes.routes,
        new ProgressRoutes(progressActor).routes,
        new DownloadRoutes(dispatchActor).routes,
        new StatusRoutes(statusActor, userDirectives).routes,
        new ScalaJsRoutes(dispatchActor).routes,
        new AutowireApiRoutes(dispatchActor, userDirectives).routes
      )

    val publicRoutes =
      concat(
        PublicRoutes.routes,
        new OAuth2Routes(github, session).routes
      )

    val privateRoutes = concat(programmaticRoutes, userFacingRoutes)

    val routes = concat(publicRoutes, privateRoutes)

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 1.seconds)
    logger.info(s"Scastie started (port: $port)")

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
