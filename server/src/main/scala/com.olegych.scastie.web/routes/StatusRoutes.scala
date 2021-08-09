package com.olegych.scastie.web.routes

import akka.NotUsed
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.ws.TextMessage._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, _}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.stream.scaladsl._
import akka.util.Timeout
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import com.olegych.scastie.web.oauth2.UserDirectives
import play.api.libs.json.Json

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class StatusRoutes(
  statusActor: ActorRef[StatusActor.Message], userDirectives: UserDirectives
)(implicit ec: ExecutionContext, scheduler: Scheduler) {

  val isAdminUser: Directive1[Boolean] =
    userDirectives.optionalLogin.map(
      user => user.exists(_.isAdmin)
    )

  val routes: Route =
    isAdminUser { isAdmin =>
      concat(
        path("status-sse")(
          complete(
            statusSource(isAdmin).map { progress =>
              ServerSentEvent(
                Json.stringify(Json.toJson(progress))
              )
            }
          )
        ),
        path("status-ws")(
          handleWebSocketMessages(webSocketProgress(isAdmin))
        )
      )
    }

  private def statusSource(isAdmin: Boolean) = {
    def hideTask(progress: StatusProgress): StatusProgress =
      if (isAdmin) progress
      else
        progress match {
          case StatusProgress.Sbt(runners) =>
            // Hide the task Queue for non admin users,
            // they will only see the runner count
            StatusProgress.Sbt(
              runners.map(_.copy(tasks = Vector()))
            )

          case _ =>
            progress
        }

    implicit val timeout: Timeout = 2.second
    Source
      .future(statusActor.ask(SubscribeStatus.apply))
      .flatMapConcat(s => s.map(hideTask))
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
        case Strict(c) => Future.successful(c)
        case e => Future.failed(new Exception(e.toString))
      }
      .via(flow)
      .map(
        progress => ws.TextMessage.Strict(Json.stringify(Json.toJson(progress)))
      )
  }
}
