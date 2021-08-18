package com.olegych.scastie.web.routes

import com.olegych.scastie.balancer.{DispatchActor, DownloadSnippet}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.actor.typed.{ActorRef, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable

import akka.util.Timeout
import scala.concurrent.duration.DurationInt

class DownloadRoutes(
  dispatchActor: ActorRef[DispatchActor.Message]
)(implicit scheduler: Scheduler) {
  private implicit val timeout: Timeout = Timeout(5.seconds)

  val routes: Route =
    get {
      snippetIdStart("download")(
        sid =>
          onSuccess(dispatchActor.ask(DownloadSnippet(_, sid))) {
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
