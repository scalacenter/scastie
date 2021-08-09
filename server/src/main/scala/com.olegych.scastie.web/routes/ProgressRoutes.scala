package com.olegych.scastie.web.routes

import akka.NotUsed
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.coding.Coders.Gzip
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.ws.TextMessage._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.stream.scaladsl._
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import akka.util.Timeout
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProgressRoutes(progressActor: ActorRef[ProgressMessage])(implicit scheduler: Scheduler) {
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
    implicit val timeout: Timeout = 1.second
    Source
      .future(progressActor.ask[Source[SnippetProgress, NotUsed]](SubscribeProgress(snippetId, _)))
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
