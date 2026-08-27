package org.scastie
package web
package routes

import oauth2._

import com.softwaremill.pekkohttpsession.SessionDirectives._
import com.softwaremill.pekkohttpsession.SessionOptions._
import com.softwaremill.pekkohttpsession.CsrfDirectives._
import com.softwaremill.pekkohttpsession.CsrfOptions._

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.Uri.Query
import org.apache.pekko.http.scaladsl.model.StatusCodes.TemporaryRedirect
import org.apache.pekko.http.scaladsl.model.headers.Referer
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import scala.concurrent.ExecutionContext
import org.scastie.api.User
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import org.scastie.api.UserData

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
                      "scope" -> "read:org",
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
            parameters("code", "state".?) { (code, state) =>
              onSuccess(github.getUserDataWithOauth2(code)) { userData =>
                setSession(refreshable, usingCookies, session.addUserData(userData)) {
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
    ) ~
    post(
      path("api" / "changeUser") {
        entity(as[User]) { requestedUser =>
          requiredSession(refreshable, usingCookies) { sessionId =>
            val currentUserDataOpt: Option[UserData] = session.getUserData(Some(sessionId))
            val canSwitch = currentUserDataOpt.exists { userData =>
              userData.switchableUsers.exists(_.login == requestedUser.login)
            }
            if (canSwitch) {
              val newUserUUID = session.switchUser(currentUserDataOpt, requestedUser)
              val newUserDataOption = session.getUserData(Some(newUserUUID))
              val newUserData = newUserDataOption.getOrElse(UserData(requestedUser, List.empty))
              invalidateSession(refreshable, usingCookies) {
                setSession(refreshable, usingCookies, newUserUUID) {
                  setNewCsrfToken(checkHeader) { ctx =>
                    ctx.complete(
                      HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          newUserData.asJson.noSpaces
                        )
                      )
                    )
                  }
                }
              }
            }
            else {
              complete(StatusCodes.Forbidden)
            }
          }
        }
      }
    )
}
