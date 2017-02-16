package com.olegych.scastie
package web
package oauth2

import api.User

import akka.http.scaladsl._
import server._
import Directives._
import model._
// import Uri.Path
import StatusCodes.TemporaryRedirect

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._

class UserDirectives(session: GithubUserSession) {
  import session._

  def requireLogin: Directive0 = {
    optionalSession(refreshable, usingCookies).flatMap { userId =>
      if (getUser(userId).nonEmpty) pass
      else redirect(Uri("/beta"), TemporaryRedirect)
    }
  }

  def userLogin: Directive1[User] = {
    optionalSession(refreshable, usingCookies).flatMap { userId =>
      getUser(userId) match {
        case Some(user) => provide(user)
        case None => reject
      }
    }
  }
}
