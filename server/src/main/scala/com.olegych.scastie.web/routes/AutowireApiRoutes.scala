package com.olegych.scastie
package web
package routes

import api._
import oauth2._

import balancer._

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import akka.pattern.ask

import upickle.default.{read => uread}

import scala.concurrent.duration.DurationInt

class AutowireApiRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem) {
  import system.dispatcher
  import userDirectives.optionalLogin

  implicit val timeout = Timeout(5.seconds)

  val routes: Route =
    post(
      extractClientIP(
        remoteAddress ⇒
          optionalLogin(
            user ⇒
              concat(
                // temporary route for the scala-lang frontpage
                path("scala-lang")(
                  cors()(
                    entity(as[String]) { code ⇒
                      val inputs =
                        InputsWithIpAndUser(
                          Inputs.default.copy(code = code),
                          remoteAddress.toString,
                          None
                        )
                      complete(
                        (dispatchActor ? RunSnippet(inputs))
                          .mapTo[SnippetId]
                          .map(
                            snippetId =>
                              (
                                Created,
                                snippetId.url
                            )
                          )
                      )
                    }
                  )
                ),
                path("api" / Segments)(
                  s ⇒
                    entity(as[String])(
                      e ⇒
                        complete {
                          val api = new AutowireApiImplementation(
                            dispatchActor,
                            remoteAddress,
                            user
                          )

                          AutowireServer.route[AutowireApi](api)(
                            autowire.Core.Request(
                              s,
                              uread[Map[String, String]](e)
                            )
                          )
                      }
                  )
                )
            )
        )
      )
    )
}
