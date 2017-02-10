package com.olegych.scastie
package web
package routes

import oauth2._
import views._
import TwirlSupport._


import akka.http.scaladsl._
import server.Directives._

// import com.softwaremill.session._
// import SessionDirectives._
// import SessionOptions._

class FrontPage(session: GithubUserSession) {
  // import session._

  // def index =
  //   optionalSession(refreshable, usingCookies)(userId =>
  //     // session.getUser(userId).map(_.user)
  //     complete(html.index())   
  //   )

  // def requireLogin[T](f: => T): T =
  //   optionalSession(refreshable, usingCookies)(userId =>  
  //     session.getUser(userId).map(_.user) match {
  //       case Some(user) => complete(f)
  //       case None => redirect("/beta")
  //     }
  //   )    

  val routes = (
      get(
        concat(
          pathSingleSlash(
            complete(html.index())
          ),
          path("embedded-demo")(
            complete(html.embedded())
          ),
          path(Segment)(_ => 
            complete(html.index())
          )
        )
      )
  )
}
