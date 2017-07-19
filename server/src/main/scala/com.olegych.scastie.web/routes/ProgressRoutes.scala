package com.olegych.scastie
package web
package routes

import SnippetIdDirectives._

import api._
import balancer._

import de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._

import akka.util.Timeout
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import akka.stream.scaladsl._

import upickle.default.{write => uwrite, read => uread}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

class ProgressRoutes(progressActor: ActorRef) {
  implicit private val timeout = Timeout(1.seconds)

  val routes: Route =
    concat(
      snippetId("progress-sse")(
        sid ⇒
          complete(
            progressSource(sid)
              .map(progress => ServerSentEvent(uwrite(progress)))
        )
      ),
      snippetId("progress-websocket")(
        sid => handleWebSocketMessages(webSocketProgress(sid))
      )
    )

  private def progressSource(snippetId: SnippetId) = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (progressActor ? SubscribeProgress(snippetId))
        .mapTo[Source[SnippetProgress, NotUsed]],
      1.second
    )
  }

  private def webSocketProgress(
      snippetId: SnippetId
  ): Flow[ws.Message, ws.Message, _] = {
    def flow: Flow[KeepAlive, SnippetProgress, NotUsed] = {
      val in = Flow[KeepAlive].to(Sink.ignore)
      val out = progressSource(snippetId)
      Flow.fromSinkAndSource(in, out)
    }

    Flow[ws.Message]
      .mapAsync(1) {
        case Strict(c) ⇒ Future.successful(c)
        case e => Future.failed(new Exception(e.toString))
      }
      .map(uread[KeepAlive](_))
      .via(flow)
      .map(progress ⇒ ws.TextMessage.Strict(uwrite(progress)))
  }
}
