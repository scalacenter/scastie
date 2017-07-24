package com.olegych.scastie.web.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object AssetsRoutes {
  val routes: Route =
    get(
      concat(
        path("assets" / "lib" / Remaining)(
          path ⇒ getFromResource("lib/" + path)
        ),
        path("assets" / "public" / Remaining)(
          path ⇒ getFromResource("public/" + path)
        ),
        path("assets" / "client-opt.js")(
          getFromResource("client-opt.js")
        ),
        path("assets" / "client-opt.js.map")(
          getFromResource("client-opt.js.map")
        )
      )
    )
}
