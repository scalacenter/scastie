package com.olegych.scastie
package web
package routes

import SnippetIdDirectives._

import api._

import akka.util.Timeout

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl._, server.Directives._

import scala.concurrent.duration.DurationInt

class ScalaJsRoutes(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  implicit val timeout = Timeout(1.seconds)

  val routes =
    concat(
      snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.targetFilename)(
        sid =>
          complete(
            (dispatchActor ? FetchScalaJs(sid))
              .mapTo[Option[FetchResultScalaJs]]
              .map(_.map(_.content))
        )
      ),
      snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.sourceFilename)(
        sid =>
          complete(
            (dispatchActor ? FetchScalaSource(sid))
              .mapTo[Option[FetchResultScalaSource]]
              .map(_.map(_.content))
        )
      ),
      snippetIdEnd(Shared.scalaJsHttpPathPrefix,
                   ScalaTarget.Js.sourceMapFilename)(
        sid =>
          complete(
            (dispatchActor ? FetchScalaJsSourceMap(sid))
              .mapTo[Option[FetchResultScalaJsSourceMap]]
              .map(_.map(_.content))
        )
      )
    )
}
