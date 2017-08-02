package com.olegych.scastie.web.routes

import com.olegych.scastie.web._
import com.olegych.scastie.api._
import com.olegych.scastie.web.oauth2._

import com.olegych.scastie.balancer._

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}

import akka.http.scaladsl.model.StatusCodes.Created
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import akka.pattern.ask

import scala.concurrent.duration.DurationInt

class ApiRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem)
    extends PlayJsonSupport {

  import system.dispatcher
  import userDirectives.optionalLogin
  import SnippetIdDirectives.snippetId
  implicit val timeout = Timeout(5.seconds)

  import play.api.libs.json._
  implicit val readsInputs = Json.reads[Inputs]

  val withRestApiServer: Directive1[RestApiServer] =
    (extractClientIP & optionalLogin).tmap {
      case (remoteAddress, user) =>
        new RestApiServer(dispatchActor, remoteAddress, user)
    }

  val routes: Route =
    pathPrefix("api")(
      withRestApiServer(
        server =>
          concat(
            post(
              concat(
                path("run")(
                  entity(as[Inputs])(inputs => complete(server.run(inputs)))
                ),
                path("save")(
                  entity(as[Inputs])(inputs => complete(server.save(inputs)))
                ),
                path("amend")(
                  entity(as[EditInputs])(
                    editInputs => complete(server.amend(editInputs))
                  )
                ),
                path("update")(
                  entity(as[EditInputs])(
                    editInputs => complete(server.update(editInputs))
                  )
                ),
                path("fork")(
                  entity(as[EditInputs])(
                    editInputs => complete(server.fork(editInputs))
                  )
                ),
                path("delete")(
                  entity(as[SnippetId])(
                    snippetId => complete(server.delete(snippetId))
                  )
                ),
                path("autocomplete")(
                  entity(as[AutoCompletionRequest])(
                    request => complete(server.autocomplete(request))
                  )
                ),
                path("typeAt")(
                  entity(as[TypeAtPointRequest])(
                    request => complete(server.typeAt(request))
                  )
                ),
                path("format")(
                  entity(as[FormatRequest])(
                    request => complete(server.format(request))
                  )
                )
              )
            ),
            get(
              concat(
                snippetId("snippets")(
                  sid => complete(server.fetch(sid))
                ),
                path("old-snippets" / IntNumber)(
                  id => complete(server.fetchOld(id))
                ),
                path("user" / "settings")(
                  complete(server.fetchUser())
                ),
                path("user" / "snippets")(
                  complete(server.fetchUserSnippets())
                )
              )
            )
        )
      )
    )
}
