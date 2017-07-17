package com.olegych.scastie
package web
package routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object DebugRoutes {

  val routes: Route =
    path("exception-debug")(
      complete {
        throw new Exception("Boom")
        "OK"
      }
    )
}
