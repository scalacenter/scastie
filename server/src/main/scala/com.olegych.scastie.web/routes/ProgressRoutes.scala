package com.olegych.scastie.web.routes

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.ws.TextMessage._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl._
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProgressRoutes(progressActor: ActorRef) {
  val routes: Route = encodeResponseWith(Gzip)(
    concat(
      snippetIdStart("progress-sse") { sid =>
        complete {
          progressSource(sid).map { progress =>
            ServerSentEvent(Json.stringify(Json.toJson(progress)))
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
        progress => ws.TextMessage.Strict(Json.stringify(Json.toJson(progress)))
      )
  }
}
