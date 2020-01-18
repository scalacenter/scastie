package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import com.olegych.scastie.web.oauth2._

import com.olegych.scastie.balancer._

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}

import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import akka.pattern.ask

import scala.concurrent.duration.DurationInt

// temporary route for the scala-lang frontpage
class ScalaLangRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem) {
  import system.dispatcher
  import userDirectives.optionalLogin

  implicit val timeout: Timeout = Timeout(5.seconds)

  // format: off
  val routes: Route =
    post(
      extractClientIP(remoteAddress =>
        optionalLogin(user =>
          path("scala-lang")(
            entity(as[String]) { code =>
              val inputs =
                InputsWithIpAndUser(
                  Inputs.default.copy(code = code),
                  UserTrace(
                    remoteAddress.toString,
                    None
                  )
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
        )
      )
    )
}
