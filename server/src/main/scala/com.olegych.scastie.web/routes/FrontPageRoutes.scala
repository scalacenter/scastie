package com.olegych.scastie.web.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

class FrontPageRoutes(production: Boolean) {

  private val index =
    complete(
      serveStatic(
        getResource("/public/views/index.html").map(substituteScalaJs)
      )
    )

  val routes: Route =
    get(
      concat(
        pathSingleSlash(index),
        path("embedded-demo")(
          complete(
            serveStatic(
              getResource("/public/views/embedded.html").map(substituteScalaJs)
            )
          )
        ),
        path(Segment ~ Slash.?)(_ => index),
        path(Segment / Segment ~ Slash.?)((_, _) => index),
        path(Segment / Segment / Segment ~ Slash.?)((_, _, _) => index)
      )
    )

  private def substituteScalaJs(content: String): String = {
    if (!production)
      content
    else
      content.replaceAllLiterally(
        """<script src="/assets/public/client-fastopt.js"></script>""",
        """<script src="/assets/client-opt.js"></script>"""
      )
  }
}
