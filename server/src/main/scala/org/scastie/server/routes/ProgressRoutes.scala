package org.scastie.web.routes

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.coding.Coders.Gzip
import org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.model.ws.TextMessage._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.pattern.ask
import org.apache.pekko.stream.scaladsl._
import org.scastie.api._
import org.scastie.balancer._
import io.circe.syntax._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProgressRoutes(progressActor: ActorRef) {
  val routes: Route = encodeResponseWith(Gzip)(
    concat(
      snippetIdStart("progress-sse") { sid =>
        complete {
          progressSource(sid).map { progress =>
            ServerSentEvent(progress.asJson.noSpaces)
          }
        }
      },
      snippetIdStart("progress-ws")(
        sid => handleWebSocketMessages(webSocket(sid))
      )
    )
  )

  private def progressSource(
      snippetId: SnippetId
  ): Source[SnippetProgress, NotUsed] = {
    Source
      .fromFuture((progressActor ? SubscribeProgress(snippetId))(1.second).mapTo[Source[SnippetProgress, NotUsed]])
      .flatMapConcat(identity)
  }

  private def webSocket(snippetId: SnippetId): Flow[ws.Message, ws.Message, _] = {
    def flow: Flow[String, SnippetProgress, NotUsed] = {
      val in = Flow[String].to(Sink.ignore)
      val out = progressSource(snippetId)
      Flow.fromSinkAndSource(in, out)
    }

    Flow[ws.Message]
      .mapAsync(1) {
        case Strict(c) => Future.successful(c)
        case e         => Future.failed(new Exception(e.toString))
      }
      .via(flow)
      .map(
        progress => ws.TextMessage.Strict(progress.asJson.noSpaces)
      )
  }
}
