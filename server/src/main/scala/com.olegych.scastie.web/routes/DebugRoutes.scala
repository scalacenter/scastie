package com.olegych.scastie
package web
package routes

import akka.http.scaladsl._
import server.Directives._

object DebugRoutes {

  val routes =
    path("exception-debug")(
      complete {
        throw new Exception("Boom")
        "OK"
      }
    )
}
