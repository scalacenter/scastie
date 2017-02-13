package com.olegych.scastie
package web
package routes

import views._
import TwirlSupport._

import akka.http.scaladsl._

import server.Directives._

class FrontPage(production: Boolean) {
  val routes = (
    get(
      concat(
        pathSingleSlash(
          complete(html.index(production))
        ),
        path("embedded-demo")(
          complete(html.embedded(production))
        ),
        path(Segment)(_ => complete(html.index(production)))
      )
    )
  )
}
