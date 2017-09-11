package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import com.olegych.scastie.balancer._

import play.api.libs.json.Json

import de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._

import akka.util.Timeout
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class ProgressRoutes(progressActor: ActorRef) {
  implicit private val timeout = Timeout(1.seconds)

  val routes: Route =
    concat(
      snippetIdStart("progress-sse")(
        sid ⇒
          complete(
            progressSource(sid)
              .map(
                progress =>
                  ServerSentEvent(Json.stringify(Json.toJson(progress)))
              )
        )
      ),
      snippetIdStart("progress-websocket")(
        sid => handleWebSocketMessages(webSocket(sid))
      )
    )

  private def progressSource(
      snippetId: SnippetId
  ): Source[SnippetProgress, NotUsed] = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (progressActor ? SubscribeProgress(snippetId))
        .mapTo[Source[SnippetProgress, NotUsed]],
      1.second
    )
  }

  private def webSocket(snippetId: SnippetId): Flow[ws.Message, ws.Message, _] = {
    def flow: Flow[String, SnippetProgress, NotUsed] = {
      val in = Flow[String].to(Sink.ignore)
      val out = progressSource(snippetId)
      Flow.fromSinkAndSource(in, out)
    }

    Flow[ws.Message]
      .mapAsync(1) {
        case Strict(c) ⇒ Future.successful(c)
        case e => Future.failed(new Exception(e.toString))
      }
      .via(flow)
      .map(
        progress ⇒ ws.TextMessage.Strict(Json.stringify(Json.toJson(progress)))
      )
  }
}
