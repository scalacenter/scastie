package scastie.server.routes

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.scaladsl._
import com.olegych.scastie.api._
import com.olegych.scastie.balancer._
import play.api.libs.json.Json

import scala.concurrent.duration.DurationInt
import akka.stream.scaladsl.Source
import sttp.model.sse.ServerSentEvent

import scala.concurrent.Future
import scastie.endpoints.ProgressEndpoints
import scala.concurrent.ExecutionContext

class ProgressRoutesImpl(progressActor: ActorRef)(implicit ec: ExecutionContext) {

  val progressSSEImpl = ProgressEndpoints.progressSSE.map { endpoint =>
    endpoint.serverLogicSuccess[Future](snippetId =>
      progressActor.ask(SubscribeProgress(snippetId))(1.second)
        .mapTo[Source[SnippetProgress, NotUsed]]
        .map(_.map(progress => ServerSentEvent(Some(Json.stringify(Json.toJson(progress))))))
      )
  }

  val progressWSImpl = ProgressEndpoints.progressWS.map { endpoint =>
    endpoint.serverLogicSuccess[Future](snippetId => {
      progressActor.ask(SubscribeProgress(snippetId))(1.second)
        .mapTo[Source[SnippetProgress, NotUsed]]
        .map { source =>
            val in = Flow[String].to(Sink.ignore)
            Flow.fromSinkAndSource(in, source)
        }
    })
  }

  val serverEndpoints = progressSSEImpl ++ progressWSImpl
}
