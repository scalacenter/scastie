package com.olegych.scastie.web.routes

import com.olegych.scastie.balancer.DownloadSnippet

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import akka.actor.ActorRef
import akka.pattern.ask

import java.nio.file.Path

import akka.util.Timeout
import scala.concurrent.duration.DurationInt

class DownloadRoutes(dispatchActor: ActorRef) {
  implicit val timeout = Timeout(5.seconds)

  val routes: Route =
    get {
      snippetIdStart("download")(
        sid =>
          onSuccess((dispatchActor ? DownloadSnippet(sid)).mapTo[Option[Path]]) {
            case Some(path) =>
              getFromFile(path.toFile)
            case None =>
              throw new Exception(
                s"Can't serve project ${sid.base64UUID} to user ${sid.user.getOrElse("anon")}"
              )
        }
      )
    }
}
