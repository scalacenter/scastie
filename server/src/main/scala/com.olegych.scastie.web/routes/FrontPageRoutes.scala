package com.olegych.scastie.web.routes

import com.olegych.scastie.api.{SnippetId, SnippetUserPart}

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import java.util.UUID
import System.{lineSeparator => nl}

object FrontPageRoutes {

  private val index = getFromResource("public/index.html")

  private def embeddedRessource(snippetId: SnippetId): String = {
    val user =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          s"user: '$login', update: $update,"
        }
        case None => ""
      }

    val id = UUID.randomUUID().toString

    s"""|document.write("
        |<div id='$id'></div>
        |<script>
        |  com.olegych.scastie.client.ClientMain.embedded({
        |    base64UUID: '${snippetId.base64UUID}',
        |    $user
        |    injectId: '$id'
        |  });
        |</script>
        |");""".stripMargin.split(nl).map(_.trim).mkString("")
  }

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
          pathSingleSlash(index),
          snippetId(_ => index),
          snippetIdExtension(".js")(sid => complete(embeddedRessource(sid)))
        )
      )
    )
}
