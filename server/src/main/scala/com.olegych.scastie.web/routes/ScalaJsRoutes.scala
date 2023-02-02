package com.olegych.scastie.web.routes

import scala.concurrent.duration.DurationInt

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.coding.Coders.Gzip
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.olegych.scastie.api._

//not used anymore
class ScalaJsRoutes(dispatchActor: ActorRef)(
  implicit system: ActorSystem
) {
  import system.dispatcher

  implicit val timeout: Timeout = Timeout(1.seconds)

  val routes: Route = encodeResponseWith(Gzip)(
    concat(
      snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.targetFilename)(sid =>
        complete(
          (dispatchActor ? FetchScalaJs(sid))
            .mapTo[Option[FetchResultScalaJs]]
            .map(_.map(_.content))
        )
      ),
      snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.sourceFilename)(sid =>
        complete(
          (dispatchActor ? FetchScalaSource(sid))
            .mapTo[Option[FetchResultScalaSource]]
            .map(_.map(_.content))
        )
      ),
      snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.sourceMapFilename)(sid =>
        complete(
          (dispatchActor ? FetchScalaJsSourceMap(sid))
            .mapTo[Option[FetchResultScalaJsSourceMap]]
            .map(_.map(_.content))
        )
      )
    )
  )

}
