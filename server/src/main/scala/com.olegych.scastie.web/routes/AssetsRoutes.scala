package ch.epfl.scala.index
package server
package routes

import akka.http.scaladsl.server.Directives._

object Assets (
  val routes =
    get(
      concat(
        path("assets" / "lib" / Remaining)( path ⇒
          getFromResource("lib/" + path)
        ),
        path("assets" / "public" / Remaining)( path ⇒
          getFromResource("public/" + path)
        ),
        path("assets" / "client-opt.js")(
          getFromResource("client-opt.js")
        ),
        path("assets" / "client-fastopt.js")(
          getFromResource("client-fastopt.js")
        ),
        path("assets" / "client-opt.js.map")(
          getFromResource("client-opt.js.map")
        ),
        path("assets" / "client-fastopt.js.map")(
          getFromResource("client-fastopt.js.map")
        ),
        path("assets" / "client-jsdeps.js")(
          getFromResource("client-jsdeps.js")
        )
      )
    )
)
