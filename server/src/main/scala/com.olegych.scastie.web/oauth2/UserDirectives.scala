package com.olegych.scastie.web.oauth2

import com.olegych.scastie.api.User

import akka.http.scaladsl._
import server._

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._

class UserDirectives(session: GithubUserSession) {
  import session._

  def optionalLogin: Directive1[Option[User]] =
    optionalSession(refreshable, usingCookies).map(getUser)
}
