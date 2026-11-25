package org.scastie.web.routes

import org.scastie.balancer.DownloadSnippet

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.model.headers.RawHeader

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.pattern.ask

import java.nio.file.Path

import org.apache.pekko.util.Timeout
import scala.concurrent.duration.DurationInt

class DownloadRoutes(dispatchActor: ActorRef) {
  implicit val timeout = Timeout(5.seconds)

  val routes: Route =
    get {
      snippetIdStart("download")(
        sid =>
          onSuccess((dispatchActor ? DownloadSnippet(sid)).mapTo[Option[Path]]) {
            case Some(path) =>
              val fileName = path.getFileName.toString
              respondWithHeader(RawHeader("Content-Disposition", s"attachment; filename=\"$fileName\"")) {
                getFromFile(path.toFile)
              }
            case None =>
              throw new Exception(
                s"Can't serve project ${sid.base64UUID} to user ${sid.user.getOrElse("anon")}"
              )
        }
      )
    }
}
