package com.olegych.scastie
package web
package routes

import akka.http.scaladsl.server.Directives._

object AssetsRoutes {
  val routes =
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
