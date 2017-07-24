package com.olegych.scastie.web.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object PublicRoutes {
  val routes: Route =
    concat(
      get(
        concat(
          path("beta")(
            getFromResource("public/views/beta.html")
          ),
          path("beta-full")(
            getFromResource("public/views/beta-full.html")
          )
        )
      ),
      AssetsRoutes.routes
    )
}
