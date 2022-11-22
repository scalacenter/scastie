package com.olegych.scastie.web.routes

import akka.actor.ActorRef
import akka.http.scaladsl.coding.Coders.{Gzip, NoCoding}
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.{ByteString, Timeout}
import com.olegych.scastie.api.{FetchResult, SnippetId, SnippetUserPart}
import com.olegych.scastie.balancer.FetchSnippet
import com.olegych.scastie.util.Base64UUID
import org.apache.commons.text.StringEscapeUtils

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class FrontPageRoutes(dispatchActor: ActorRef, production: Boolean)(implicit ec: ExecutionContext, mat: Materializer) {
  implicit val timeout: Timeout = Timeout(20.seconds)
  private val placeholders = List(
    "Scastie can run any Scala program with any library in your browser. You don’t need to download or install anything.",
  )
  private val indexResource = "public/index.html"
  private val indexResourceContent = Future.traverse(Option(getClass.getClassLoader.getResource(indexResource)).toList) { url =>
    StreamConverters.fromInputStream(() => url.openStream()).runFold("")(_ + _.utf8String)
  }
  private val index = getFromResource(indexResource)

  private def embeddedResource(snippetId: SnippetId, theme: Option[String]): String = {
    val user = snippetId.user match {
      case Some(SnippetUserPart(login, update)) =>
        s"user: '$login', update: $update,"
      case None => ""
    }

    val themePart = theme match {
      case Some(t) => s"theme: '$t',"
      case None    => ""
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
        |  scastie.EmbeddedResource({
        |    $themePart
        |    base64UUID: '${snippetId.base64UUID}',
        |    $user
        |    injectId: '$id',
        |    serverUrl: '$embeddedUrlBase'
        |  });
        |});
        |</script>
        |");""".stripMargin.split("\n").map(_.trim).mkString("")
  }

  val routes: Route = encodeResponseWith(Gzip, NoCoding)(
    get(
      concat(
        path("public" / "app.css")(
          getFromResource("public/assets/index.css")
        ),
        path("public" / "app.js")(
          getFromResource("public/app.js")
        ),
        path("public" / "app.js.map")(
          getFromResource("public/app.js.map")
        ),
        path("public" / "embedded.css")(
          getFromResource("public/assets/index.css")
        ),
        path("embedded.js")(
          getFromResource("public/embedded.js")
        ),
        path("public" / Remaining)(
          path => getFromResource("public/" + path)
        ),
        pathSingleSlash(index),
        snippetId { snippetId => ctx =>
          for {
            s <- dispatchActor.ask(FetchSnippet(snippetId)).mapTo[Option[FetchResult]]
            c <- indexResourceContent
            r <- index(ctx)
          } yield
            (r, c, s) match {
              case (r: RouteResult.Complete, List(c), Some(s)) if r.response.status.intValue() == 200 =>
                val code = StringEscapeUtils.escapeHtml4(s.inputs.code)
                r.copy(
                  response = r.response.withEntity(
                    HttpEntity.Strict(
                      r.response.entity.contentType,
                      ByteString.fromString(placeholders.foldLeft(c)(_.replace(_, code))),
                    )
                  )
                )
              case _ => r
            }
        },
        parameter("theme".?) { theme =>
          snippetIdExtension(".js") { sid =>
            complete(embeddedResource(sid, theme))
          }
        },
        index,
      )
    )
  )
}
