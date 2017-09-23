package com.olegych.scastie.web.routes

import com.olegych.scastie.util.Base64UUID
import com.olegych.scastie.api.{SnippetId, SnippetUserPart}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import System.{lineSeparator => nl}

class FrontPageRoutes(production: Boolean) {

  private val index = getFromResource("public/index.html")

  private def embeddedRessource(snippetId: SnippetId): String = {
    val user =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          s"user: '$login', update: $update,"
        }
        case None => ""
      }

    val id = "id-" + Base64UUID.create

    val embeddedUrlBase =
      if (production) "https://scastie.scala-lang.org"
      else "http://localhost:9000"

    s"""|document.write("
        |<div id='$id'></div>
        |<script src='$embeddedUrlBase/embedded.js'></script>
        |<script>
        |window.addEventListener('load', function(event) {
        |  scastie.EmbeddedRessource({
        |    base64UUID: '${snippetId.base64UUID}',
        |    $user
        |    injectId: '$id',
        |    serverUrl: '$embeddedUrlBase'
        |  });
        |});
        |</script>
        |");""".stripMargin.split(nl).map(_.trim).mkString("")
  }

  val routes: Route =
    concat(
      get(
        concat(
          path("public" / "app.css")(
            getFromResource("public/app.css.gz")
          ),
          path("public" / "app.js")(
            getFromResource("public/app.js.gz")
          ),
          path("public" / "embedded.css")(
            getFromResource("public/embedded.css.gz")
          ),
          path("embedded.js")(
            getFromResource("public/embedded.js.gz")
          ),
          path("embedded.js.map")(
            getFromResource("public/embedded.js.map")
          ),
          path("public" / Remaining)(
            path ⇒ getFromResource("public/" + path)
          )
        )
      ),
      get(
        concat(
          pathSingleSlash(index),
          snippetId(_ => index),
          snippetIdExtension(".js")(sid => complete(embeddedRessource(sid)))
        )
      )
    )
}




