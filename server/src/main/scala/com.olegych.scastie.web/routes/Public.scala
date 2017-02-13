package com.olegych.scastie
package web
package routes

import TwirlSupport._
import akka.http.scaladsl.server.Directives._

object Public {
  val routes =
    concat(
      get(
        path("beta")(
          complete(views.html.beta())
        )
      ),
      Assets.routes
    )
}
