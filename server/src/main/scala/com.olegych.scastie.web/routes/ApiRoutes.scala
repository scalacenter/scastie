package com.olegych.scastie.web.routes

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import com.olegych.scastie.api._
import com.olegych.scastie.web._
import com.olegych.scastie.web.oauth2._

class ApiRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem)
    extends PlayJsonSupport {

  import play.api.libs.json._
  import system.dispatcher
  import userDirectives.optionalLogin
  implicit val readsInputs: Reads[Inputs] = Json.reads[Inputs]

  val withRestApiServer: Directive1[RestApiServer] =
    (extractClientIP & optionalLogin).tmap {
      case (remoteAddress, user) =>
        new RestApiServer(dispatchActor, remoteAddress, user)
    }

  val routes: Route =
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
              path("format")(
                entity(as[FormatRequest])(
                  request => complete(server.format(request))
                )
              )
            )
          ),
          encodeResponseWith(Gzip)(
            get(
              concat(
                snippetIdStart("snippets")(
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
