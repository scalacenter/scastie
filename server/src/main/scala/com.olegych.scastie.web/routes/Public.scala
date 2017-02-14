package com.olegych.scastie
package web
package routes

import TwirlSupport._
import akka.http.scaladsl.server.Directives._

import akka.NotUsed
import akka.http.scaladsl._
import model._
import ws.TextMessage._
import server.Directives._
import akka.stream.scaladsl._

import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.EventStreamMarshalling._

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt

import scala.concurrent.Future

object Public {
  val routes =
    concat(
      get(
        path("beta")(
          complete(views.html.beta())
        )
      ),
      get(
        concat(
          path("sse-demo")(
            complete(views.html.sseDemo())
          ),
          path("websocket-demo")(
            complete(views.html.websocketDemo())
          ),
          path("demo-sse-progress" / Segment)(progressId =>
            complete(
              source(progressId).map(time => ServerSentEvent(time))
            )
          ),
          path("demo-websocket-progress" / Segment)(progressId =>
            handleWebSocketMessages(
              webSocket(progressId)
            )
          )
        )
      ),
      Assets.routes
    )

  private def source(id: String) = {
    Source
      .tick(0.second, 1.seconds, NotUsed)
      .take(5)
      .map(_ => id + " == " + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()))
  }

  private def webSocket(id: String): Flow[ws.Message, ws.Message , _] = {
    Flow[ws.Message]
      .mapAsync(1){
        case Strict(c) ⇒ Future.successful(c)
        case e => Future.failed(new Exception(e.toString))
      }
      .via(
        Flow.fromSinkAndSource(Flow[String].to(Sink.ignore), source(id))
      )
      .map(time ⇒ ws.TextMessage.Strict(time))
  }
}
