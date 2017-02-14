package com.olegych.scastie
package web
package routes

import akka.http.scaladsl.server.Directives._

object Assets {
  val routes =
    get(
      concat(
        path("assets" / "lib" / Remaining)(path ⇒
          getFromResource("lib/" + path)),
        path("assets" / "public" / Remaining)(path ⇒
          getFromResource("public/" + path))
      )
    )
}
