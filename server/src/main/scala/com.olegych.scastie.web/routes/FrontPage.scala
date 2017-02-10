package com.olegych.scastie
package web
package routes

import views.html._

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._

class FrontPage(session: GithubUserSession) {

  def index =
    optionalSession(refreshable, usingCookies)(userId =>  
      complete(frontPage(session.getUser(userId).map(_.user)))   
    )

  def requireLogin[T](f: => T): T =
    optionalSession(refreshable, usingCookies)(userId =>  
      session.getUser(userId).map(_.user) match {
        case Some(user) => complete(f)
        case None => redirect("/beta")
      }
    )    

  val routes = (
      get(
        concat(
          pathSingleSlash(requireLogin(index)),
          path("embedded-demo")(requireLogin(embedded)),


          //
          path(Segment)(_ => index)
        )
      )
      // path(Seg)
      // optionalSession(refreshable, usingCookies) { userId =>
  )

}

/*
GET    /embedded-demo  controllers.Application.embedded
GET    /assets/*file   controllers.Assets.at(path="/public", file)
GET    /tmp/*file      controllers.Application.tmp(file)
POST   /api/*path      controllers.Application.autowireApi(path: String)
GET    /progress/*id   controllers.Application.progress(id: Int)
GET    /               controllers.Application.index
GET    /embedded       controllers.Application.index
GET    /embedded/*id   controllers.Application.index2(id: Int)
GET    /*id            controllers.Application.index2(id: Int)
*/
