package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import com.olegych.scastie.web.oauth2._

import com.olegych.scastie.balancer._

import akka.util.Timeout
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import akka.actor.typed.scaladsl.AskPattern.Askable

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

// temporary route for the scala-lang frontpage
class ScalaLangRoutes(
    dispatchActor: ActorRef[DispatchActor.Message],
    userDirectives: UserDirectives
)(implicit ec: ExecutionContext, scheduler: Scheduler) {
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
                dispatchActor.ask(RunSnippet(_, inputs))
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
