package com.olegych.scastie
package web
package routes

import balancer._

import akka.http.scaladsl._
import server.Directives._

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.duration.DurationInt

class DebugRoutes(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  // import system.dispatcher

  implicit val timeout = Timeout(1.seconds)

  val routes =
    path("loadbalancer-debug")(
      onSuccess(
        (dispatchActor ? LoadBalancerStateRequest)
          .mapTo[LoadBalancerStateResponse]
      )(
        state =>
          complete(
            serveStatic(
              getResource("/public/views/loadbalancer.html").map(
                _.replaceAllLiterally(
                  "==STATE==",
                  state.loadBalancer.debug
                )
              )
            )
        )
      )
    )
}
