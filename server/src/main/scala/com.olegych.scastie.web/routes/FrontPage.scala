package com.olegych.scastie
package web
package routes

import akka.http.scaladsl._

import server.Directives._

class FrontPage(production: Boolean) {

  val routes = (
    get(
      concat(
        pathSingleSlash(index),
        path("embedded-demo")(
          complete(serveStatic(
            getResource("/public/views/embedded.html").map(substituteScalaJs)))
        ),
        path(Segment)(_ => index),
        path(Segment / Segment)((_, _) => index)
      )
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

  private val index =
    complete(
      serveStatic(
        getResource("/public/views/index.html").map(substituteScalaJs))
    )
}
