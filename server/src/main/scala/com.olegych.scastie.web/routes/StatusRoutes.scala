package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import com.olegych.scastie.web.oauth2.UserDirectives

import play.api.libs.json.Json

import de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._

import akka.util.Timeout
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage._

import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

import scala.collection.immutable.Queue

class StatusRoutes(statusActor: ActorRef, userDirectives: UserDirectives) {

  implicit val timeout = Timeout(1.seconds)

  val isAdminUser: Directive1[Boolean] =
    userDirectives.optionalLogin.map(
      user => user.exists(_.isAdmin)
    )

  val routes: Route =
    isAdminUser(
      isAdmin =>
        concat(
          path("status-sse")(
            complete(
              statusSource(isAdmin).map(
                progress =>
                  ServerSentEvent(
                    Json.stringify(Json.toJson(progress))
                )
              )
            )
          ),
          path("status-ws")(
            handleWebSocketMessages(webSocketProgress(isAdmin))
          )
      )
    )

  private def statusSource(isAdmin: Boolean) = {
    def hideTask(progress: StatusProgress): StatusProgress =
      if (isAdmin) progress
      else
        progress match {
          case StatusProgress.Sbt(runners) =>
            // Hide the task Queue for non admin users,
            // they will only see the runner count
            StatusProgress.Sbt(
              runners.map(_.copy(tasks = Queue()))
            )

          case _ =>
            progress
        }

    // TODO find a way to flatten Source[Source[T]]
    Await
      .result(
        (statusActor ? SubscribeStatus)
          .mapTo[Source[StatusProgress, NotUsed]],
        1.second
      )
      .map(hideTask)
  }

  private def webSocketProgress(
      isAdmin: Boolean
  ): Flow[ws.Message, ws.Message, _] = {
    def flow: Flow[String, StatusProgress, NotUsed] = {
      val in = Flow[String].to(Sink.ignore)
      val out = statusSource(isAdmin)
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
