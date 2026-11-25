package org.scastie.web.routes

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.model.ws.TextMessage._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Route, _}
import org.apache.pekko.pattern.ask
import org.apache.pekko.stream.scaladsl._
import org.scastie.api._
import org.scastie.balancer._
import org.scastie.web.oauth2.UserDirectives
import io.circe.syntax._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class StatusRoutes(statusActor: ActorRef, userDirectives: UserDirectives)(implicit ec: ExecutionContext) {

  val isAdminUser: Directive1[Boolean] =
    userDirectives.optionalLogin.map(
      userData => userData.exists(_.isAdmin)
    )

  val routes: Route =
    isAdminUser { isAdmin =>
      concat(
        path("status-sse")(
          complete(
            statusSource(isAdmin).map { progress =>
              ServerSentEvent(progress.asJson.noSpaces)
            }
          )
        ),
        path("status-ws")(
          handleWebSocketMessages(webSocketProgress(isAdmin))
        )
      )
    }

  private def statusSource(isAdmin: Boolean) = {
    def emptyTasks(tasks: Vector[TaskId]) = tasks.map(_ => TaskId(SnippetId.empty))

    def hideTask(progress: StatusProgress): StatusProgress =
      if (isAdmin) progress
      else
        progress match {
          /* Hide details of tasks from non admin users */
          case StatusProgress.Sbt(runners) =>
            StatusProgress.Sbt(
              runners.map(runner => runner.copy(tasks = emptyTasks(runner.tasks)))
            )

          case StatusProgress.ScalaCli(runners) =>
            StatusProgress.ScalaCli(
              runners.map(runner => runner.copy(tasks = emptyTasks(runner.tasks)))
            )

          case _ =>
            progress
        }
    Source
      .fromFuture((statusActor ? SubscribeStatus)(2.seconds).mapTo[Source[StatusProgress, NotUsed]])
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
        case e         => Future.failed(new Exception(e.toString))
      }
      .via(flow)
      .map(
        progress => ws.TextMessage.Strict(progress.asJson.noSpaces)
      )
  }
}
