package com.olegych.scastie
package web

import routes._
import oauth2._
import balancer._

import akka.http.scaladsl._
// import akka.http.scaladsl.model.StatusCodes
import server.Directives._

import com.typesafe.config.ConfigFactory

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object ServerMain {
  def main(args: Array[String]): Unit = {

    val port =
      if (args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("com.olegych.scastie.web")
    val production = config.getBoolean("production")
    
    if (production) {
      val pid = ManagementFactory.getRuntimeMXBean().getName().split("@").head
      val pidFile = Paths.get("PID")
      Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
      sys.addShutdownHook {
        Files.delete(pidFile)
      }
    }

    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github
    val session = new GithubUserSession

    val progressActor = system.actorOf(Props[ProgressActor], name = "ProgressActor")
    val pasteActor = system.actorOf(Props(new DispatchActor(progressActor)), name = "PasteActor")


    def requireLogin[T](v: T): T = v // TODO

    val userFacingRoutes = 
      requireLogin(new FrontPage(session).routes)

    val programmaticRoutes = concat(
      requireLogin(new AutowireApi(pasteActor).routes),
      Assets.routes,
      new OAuth2(github, session).routes
    )

    val routes = concat(programmaticRoutes, userFacingRoutes)

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")
    println("Application started")

  }
}
