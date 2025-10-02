package org.scastie.web.routes

import java.nio.file.Path
import scala.concurrent.duration.DurationInt

import org.scastie.balancer.DownloadSnippet

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

class DownloadRoutes(dispatchActor: ActorRef) {
  implicit val timeout = Timeout(5.seconds)

  val routes: Route = get {
    snippetIdStart("download")(sid =>
      onSuccess((dispatchActor ? DownloadSnippet(sid)).mapTo[Option[Path]]) {
        case Some(path) => getFromFile(path.toFile)
        case None       => throw new Exception(
            s"Can't serve project ${sid.base64UUID} to user ${sid.user.getOrElse("anon")}"
          )
      }
    )
  }

}
