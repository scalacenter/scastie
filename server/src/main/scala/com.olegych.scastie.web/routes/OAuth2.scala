package com.olegych.scastie
package web
package routes

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._
import CsrfDirectives._
import CsrfOptions._

import akka.http.scaladsl._
import model._
import Uri.Query
import StatusCodes.TemporaryRedirect
import headers.Referer
import server.Directives._

class OAuth2(github: Github, session: GithubUserSession) {
  import session._

  val routes =
    get(
      concat(
        path("login") {
          optionalHeaderValueByType[Referer]() { referer =>
            redirect(
              Uri("https://github.com/login/oauth/authorize").withQuery(
                Query(
                  "client_id" -> github.clientId,
                  "scope" -> "read:org",
                  "state" -> referer.map(_.value).getOrElse("/")
                )),
              TemporaryRedirect
            )
          }
        },
        path("logout") {
          headerValueByType[Referer]() { referer =>
            requiredSession(refreshable, usingCookies) { _ =>
              invalidateSession(refreshable, usingCookies) { ctx =>
                ctx.complete(
                  HttpResponse(
                    status = TemporaryRedirect,
                    headers = headers.Location(Uri(referer.value)) :: Nil,
                    entity = HttpEntity.Empty
                  )
                )
              }
            }
          }
        },
        pathPrefix("callback") {
          pathEnd {
            parameters('code, 'state.?) { (code, state) =>
              onSuccess(github.getUserStateWithOauth2(code)) { userState =>
                setSession(refreshable, usingCookies, session.addUser(userState)) {
                  setNewCsrfToken(checkHeader) { ctx =>
                    ctx.complete(
                      HttpResponse(
                        status = TemporaryRedirect,
                        headers = headers.Location(Uri(state.getOrElse("/"))) :: Nil,
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
