package scastie.server.routes

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl._
import akka.stream.scaladsl.Source
import akka.NotUsed
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import play.api.libs.json.Json
import scastie.endpoints.ProgressEndpoints
import sttp.model.sse.ServerSentEvent

class ProgressRoutesImpl(progressActor: ActorRef)(implicit ec: ExecutionContext) {

  val progressSSEImpl = ProgressEndpoints.progressSSE.map { endpoint =>
    endpoint.serverLogicSuccess[Future](snippetId =>
      progressActor
        .ask(SubscribeProgress(snippetId))(1.second)
        .mapTo[Source[SnippetProgress, NotUsed]]
        .map(_.map(progress => ServerSentEvent(Some(Json.stringify(Json.toJson(progress))))))
    )
  }

  val progressWSImpl = ProgressEndpoints.progressWS.map { endpoint =>
    endpoint.serverLogicSuccess[Future](snippetId => {
      progressActor
        .ask(SubscribeProgress(snippetId))(1.second)
        .mapTo[Source[SnippetProgress, NotUsed]]
        .map { source =>
          val in = Flow[String].to(Sink.ignore)
          Flow.fromSinkAndSource(in, source)
        }
    })
  }

  val serverEndpoints = progressSSEImpl ++ progressWSImpl
}
