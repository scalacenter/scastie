package scastie.server.routes

import java.io.File
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.olegych.scastie.balancer.DownloadSnippet
import scastie.endpoints.DownloadEndpoints

class DownloadRoutesImpl(dispatchActor: ActorRef) {
  implicit val timeout = Timeout(5.seconds)

  val downloadSnippetImpl = DownloadEndpoints.downloadSnippetEndpoint.map { endpoint =>
    endpoint.serverLogicOption[Future](snippetId =>
      (dispatchActor ? DownloadSnippet(snippetId))
        .mapTo[Option[File]]
    )
  }

  val serverEndpoints = downloadSnippetImpl
}
