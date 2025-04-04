package com.olegych.scastie.web.oauth2

import scala.concurrent.ExecutionContext

import akka.http.scaladsl._
import com.olegych.scastie.api.User
import com.softwaremill.session._
import server._
import SessionDirectives._
import SessionOptions._

class UserDirectives(
  session: GithubUserSession
)(
  implicit val executionContext: ExecutionContext
) {
  import session._

  def optionalLogin: Directive1[Option[User]] = optionalSession(refreshable, usingCookies).map(getUser)
}
