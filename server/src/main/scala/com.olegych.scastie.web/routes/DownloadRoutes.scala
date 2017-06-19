package com.olegych.scastie.web.routes

import java.nio.file.Path

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import com.olegych.scastie.balancer.DownloadSnippet
import akka.pattern.ask

import scala.concurrent.duration.DurationInt
import SnippetIdDirectives._
import akka.util.Timeout
import scala.concurrent.Await

class DownloadRoutes(dispatchActor: ActorRef) {
  implicit val timeout = Timeout(5.seconds)

  val routes =
    get {
      snippetId("download")(
        sid ⇒
          Await.result(
            (dispatchActor ? DownloadSnippet(sid)).mapTo[Option[Path]],
            5.second
          ) match {
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
