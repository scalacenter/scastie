package com.olegych.scastie
package web
package routes

import akka.http.scaladsl.server.Directives._

object PublicRoutes {
  val routes =
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
