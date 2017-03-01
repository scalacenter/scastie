package com.olegych.scastie
package web

import routes._
import oauth2._
import balancer._

import akka.http.scaladsl._
import server._
import Directives._

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

    import userDirectives.requireLogin

    val progressActor =
      system.actorOf(Props[ProgressActor], name = "ProgressActor")

    val dispatchActor = system
      .actorOf(Props(new DispatchActor(progressActor)), name = "DispatchActor")

    val userFacingRoutes =
      new FrontPage(production).routes

    val programmaticRoutes =
      concat(
        new AutowireApi(dispatchActor, progressActor, userDirectives).routes,
        Assets.routes
      )

    val publicRoutes =
      concat(
        Public.routes,
        new OAuth2(github, session).routes
      )

    val privateRoutes = 
      requireLogin(
        concat(programmaticRoutes, userFacingRoutes)
      )

    val routes = concat(publicRoutes, privateRoutes)

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 1.seconds)
    logger.info(s"Application started (port: $port)")

    Await.result(system.whenTerminated, Duration.Inf)

    ()
  }
}
