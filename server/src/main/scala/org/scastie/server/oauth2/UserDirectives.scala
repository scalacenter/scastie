package org.scastie.web.oauth2

import org.scastie.api.User
import org.scastie.api.UserData

import akka.http.scaladsl._
import server._

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._

import scala.concurrent.ExecutionContext

class UserDirectives(
    session: GithubUserSession
)(implicit val executionContext: ExecutionContext) {
  import session._

  def optionalLogin: Directive1[Option[UserData]] =
    optionalSession(refreshable, usingCookies).map(getUserData)
}
