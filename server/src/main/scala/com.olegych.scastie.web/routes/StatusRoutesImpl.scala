package scastie.server.routes

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl._
import akka.stream.scaladsl.Source
import akka.NotUsed
import cats.syntax.all._
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import play.api.libs.json.Json
import scastie.endpoints.OAuthEndpoints
import scastie.endpoints.StatusEndpoints
import sttp.model.sse.ServerSentEvent
import sttp.tapir.Endpoint
import SessionManager._

object StatusRoutesImpl {

  implicit class Secure[Input, Output, R](
    endpoint: Endpoint[Option[OAuthEndpoints.JwtCookie], Input, String, Output, R]
  ) {

    def secure(implicit ec: ExecutionContext) = endpoint
      .serverSecurityLogic[Option[User], Future](optionalSession =>
        Future {
          optionalSession match {
            case Some(session) => authenticateJwt(session).toOption.asRight
            case None          => None.asRight
          }
        }
      )

  }

}

class StatusRoutesImpl(statusActor: ActorRef)(implicit ec: ExecutionContext) {
  import StatusRoutesImpl._

  def hideTask(maybeUser: Option[User], progress: StatusProgress): StatusProgress = maybeUser match {
    case Some(user) if user.isAdmin => progress
    case _ => progress match {
        case StatusProgress.Sbt(runners) => StatusProgress.Sbt(runners.map(_.withoutTasks))
        case _                           => progress
      }
  }

  val statusSSEImpl = StatusEndpoints.statusSSE.secure
    .serverLogicSuccess(maybeUser =>
      _ =>
        statusActor
          .ask(SubscribeStatus)(2.seconds)
          .mapTo[Source[StatusProgress, NotUsed]]
          .map(_.map(progress => ServerSentEvent(Some(Json.stringify(Json.toJson(hideTask(maybeUser, progress)))))))
    )

  val statusWSImpl = StatusEndpoints.statusWS.secure
    .serverLogicSuccess(maybeUser =>
      _ =>
        statusActor
          .ask(SubscribeStatus)(2.second)
          .mapTo[Source[StatusProgress, NotUsed]]
          .map { source =>
            source.map(progress => hideTask(maybeUser, progress))
            val in = Flow[String].to(Sink.ignore)
            Flow.fromSinkAndSource(in, source)
          }
    )

  val serverEndpoints = statusSSEImpl :: statusWSImpl :: Nil
}
