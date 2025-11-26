package org.scastie.web.routes

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.coding.Coders.Gzip
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import org.scastie.api._
import org.scastie.web._
import org.scastie.web.oauth2._
import org.scastie.server.utils.NightlyVersionFetcher
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

class ApiRoutes(
    dispatchActor: ActorRef,
    userDirectives: UserDirectives
)(implicit system: ActorSystem)
    extends FailFastCirceSupport {

  import system.dispatcher
  import userDirectives.optionalLogin

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
              pathPrefix("v1") {
                path("compile") {
                  entity(as[CompilationRequestV1]) { request =>
                    complete(
                      server
                        .run(SbtInputs.default.copy(code = request.code))
                        .map(snippetId => CompilationResponseV1(snippetId, snippetId.url))
                    )
                  }
                }
              },
              path("run")(
                entity(as[BaseInputs])(inputs => complete(server.run(inputs)))
              ),
              path("save")(
                entity(as[BaseInputs])(inputs => complete(server.save(inputs)))
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
                  complete(server.fetchUserData())
                ),
                path("user" / "snippets")(
                  complete(server.fetchUserSnippets())
                ),
                path("nightly-raw" / "scala2" / Segment) { prefix =>
                  complete(NightlyVersionFetcher.getLatestScala2Nightly(prefix))
                },
                path("nightly-raw" / "scala3") {
                  complete(NightlyVersionFetcher.getLatestScala3Nightly)
                }
              )
            )
          ),
          post(
            concat(
              path("user" / "privacyPolicyStatus")(
                complete(server.getPrivacyPolicy())
              ),
              path("user" / "acceptPrivacyPolicy")(
                complete(server.acceptPrivacyPolicy())
              ),
              path("user" / "removeUserFromPolicyStatus")(
                complete(server.removeUserFromPolicyStatus())
              ),
              path("user" / "removeAllUserSnippets")(
                complete(server.removeAllUserSnippets())
              ),
            )
          )
        )
    )
}
