package com.olegych.scastie
package web
package routes

import oauth2._

import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.StatusCodes.TemporaryRedirect
import akka.http.scaladsl.model.headers.Referer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext

class OAuth2Routes(github: Github, session: GithubUserSession)(
    implicit val executionContext: ExecutionContext
) {
  import session._

  val routes: Route =
    get(
      concat(
        path("login") {
          parameter("home".?)(
            home =>
              optionalHeaderValueByType[Referer](()) { referrer =>
                redirect(
                  Uri("https://github.com/login/oauth/authorize").withQuery(
                    Query(
                      "client_id" -> github.clientId,
                      "state" -> {
                        val homeUri = "/"
                        if (home.isDefined) homeUri
                        else referrer.map(_.value).getOrElse(homeUri)
                      }
                    )
                  ),
                  TemporaryRedirect
                )
            }
          )
        },
        path("logout") {
          headerValueByType[Referer](()) { referrer =>
            requiredSession(refreshable, usingCookies) { _ =>
              invalidateSession(refreshable, usingCookies) { ctx =>
                ctx.complete(
                  HttpResponse(
                    status = TemporaryRedirect,
                    headers = headers.Location(Uri(referrer.value)) :: Nil,
                    entity = HttpEntity.Empty
                  )
                )
              }
            }
          }
        },
        pathPrefix("callback") {
          pathEnd {
            parameters(("code", "state".?)) { (code, state) =>
              onSuccess(github.getUserWithOauth2(code)) { user =>
                setSession(refreshable, usingCookies, session.addUser(user)) {
                  setNewCsrfToken(checkHeader) { ctx =>
                    ctx.complete(
                      HttpResponse(
                        status = TemporaryRedirect,
                        headers = headers
                          .Location(Uri(state.getOrElse("/"))) :: Nil,
                        entity = HttpEntity.Empty
                      )
                    )
                  }
                }
              }
            }
          }
        }
      )
    )
}
