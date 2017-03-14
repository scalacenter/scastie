package com.olegych.scastie
package web
package routes

import api._
import oauth2._

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}

import akka.http.scaladsl._
import server.Directives._

import upickle.default.{read => uread}

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

class AutowireApi(dispatchActor: ActorRef, userDirectives: UserDirectives)(
    implicit system: ActorSystem) {
  import system.dispatcher
  import userDirectives.optionnalLogin

  implicit val timeout = Timeout(1.seconds)

  val routes =
    post(
      path("api" / Segments)(s ⇒
        entity(as[String])(e ⇒
          extractClientIP(remoteAddress ⇒
            optionnalLogin(user ⇒
              complete {
                val api = new ApiImplementation(
                  dispatchActor,
                  remoteAddress,
                  user
                )

                AutowireServer.route[Api](api)(
                  autowire.Core.Request(s, uread[Map[String, String]](e))
                )
            }))))
    )
}
