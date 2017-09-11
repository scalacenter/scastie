package com.olegych.scastie.web.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

class FrontPageRoutes(production: Boolean) {

  private val index = getFromResource("public/index.html")

  val routes: Route =
    concat(
      get(
        concat(
          path("public" / Remaining)(
            path ⇒ getFromResource("public/" + path)
          ),
          path("embedded.js")(getFromResource("public/embedded.js"))
        )
      ),
      get(
        concat(
          pathSingleSlash(complete("pathSingleSlash")),
          snippetId(snippetId => complete("snippetId: " + snippetId))

          // pathSingleSlash(index),
          // snippetId( _ => index)

          // path(Segment ~ Slash.?)(_ => index),
          // path(Segment / Segment ~ Slash.?)((_, _) => index),
          // path(Segment / Segment / Segment ~ Slash.?)((_, _, _) => index)
        )
      )
    )
}
