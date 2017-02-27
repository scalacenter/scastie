package com.olegych.scastie
package web
package oauth2

import api.User

import akka.http.scaladsl._
import server._
import Directives._
import model._
import StatusCodes.TemporaryRedirect

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._

class UserDirectives(session: GithubUserSession) {
  import session._

  def requireLogin: Directive0 = {
    optionalSession(refreshable, usingCookies).flatMap { userId =>
      getUser(userId) match {
        case Some(user) =>
          if (inBeta(user)) pass
          else redirect(Uri("/beta-full"), TemporaryRedirect)
        case None => redirect(Uri("/beta"), TemporaryRedirect)
      }
    }
  }

  def userLogin: Directive1[User] = {
    optionalSession(refreshable, usingCookies).flatMap { userId =>
      getUser(userId) match {
        case Some(user) =>
          if (inBeta(user)) provide(user)
          else reject
        case None => reject
      }
    }
  }

  def optionnalLogin: Directive1[Option[User]] =
    optionalSession(refreshable, usingCookies).map(getUser)
}
