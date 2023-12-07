package com.olegych.scastie.web.routes

import akka.actor.ActorRef
import akka.http.scaladsl.coding.Coders.Gzip
import akka.http.scaladsl.coding.Coders.NoCoding
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Cache-Control`, CacheDirectives}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import akka.util.Timeout
import com.olegych.scastie.api.FetchResult
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.SnippetUserPart
import com.olegych.scastie.balancer.FetchSnippet
import com.olegych.scastie.util.Base64UUID
import org.apache.commons.text.StringEscapeUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FrontPageRoutes(dispatchActor: ActorRef, production: Boolean, hostname: String)(implicit ec: ExecutionContext, mat: Materializer) {
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
      if (production) s"https://$hostname"
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
        respondWithHeader(`Cache-Control`(CacheDirectives.`no-cache`))(
          concat(
            path("embedded.js")(
              getFromResource("public/embedded/embedded.js", ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`))
            ),
            path("public" / "embedded.css")(
              getFromResource("public/embedded/style.css", ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`))
            ),
            path("public" / "tree-sitter.wasm")(
              getFromResource("public/tree-sitter.wasm", ContentType(MediaType.applicationBinary("wasm", MediaType.Compressible, "wasm")))
            ),
            path("public" / "tree-sitter-scala.wasm")(
              getFromResource("public/tree-sitter-scala.wasm", ContentType(MediaType.applicationBinary("wasm", MediaType.Compressible, "wasm")))
            ),
            path("public" / "highlights.scm")(
              getFromResource("public/highlights.scm", ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`))
            ),
          )
        ),
        respondWithHeader(`Cache-Control`(CacheDirectives.immutableDirective))(
          path("public" / Remaining)(
            path => getFromResource("public/" + path)
          ),
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
            complete {
              HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`), embeddedResource(sid, theme)))
            }
          }
        },
        index,
      )
    )
  )
}
