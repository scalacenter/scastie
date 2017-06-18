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

class AutowireApiRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem) {
  import system.dispatcher
  import userDirectives.optionalLogin

  implicit val timeout = Timeout(5.seconds)

  val routes =
    post(
      path("api" / Segments)(
        s ⇒
          entity(as[String])(
            e ⇒
              extractClientIP(
                remoteAddress ⇒
                  optionalLogin(
                    user ⇒
                      complete {
                        val api = new AutowireApiImplementation(
                          dispatchActor,
                          remoteAddress,
                          user
                        )

                        AutowireServer.route[AutowireApi](api)(
                          autowire.Core.Request(s,
                                                uread[Map[String, String]](e))
                        )
                    }
                )
            )
        )
      )
    )
}
