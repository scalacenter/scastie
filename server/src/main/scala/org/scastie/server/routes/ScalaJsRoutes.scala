package org.scastie.web.routes

import org.scastie.api._

import org.apache.pekko.util.Timeout

import org.apache.pekko.pattern.ask
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.coding.Coders.Gzip

import scala.concurrent.duration.DurationInt

//not used anymore
class ScalaJsRoutes(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  implicit val timeout: Timeout = Timeout(1.seconds)

  val routes: Route =
    encodeResponseWith(Gzip)(
      concat(
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, Js.targetFilename)(
          sid =>
            complete(
              (dispatchActor ? FetchScalaJs(sid))
                .mapTo[Option[FetchResultScalaJs]]
                .map(_.map(_.content))
          )
        ),
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, Js.sourceFilename)(
          sid =>
            complete(
              (dispatchActor ? FetchScalaSource(sid))
                .mapTo[Option[FetchResultScalaSource]]
                .map(_.map(_.content))
          )
        ),
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, Js.sourceMapFilename)(
          sid =>
            complete(
              (dispatchActor ? FetchScalaJsSourceMap(sid))
                .mapTo[Option[FetchResultScalaJsSourceMap]]
                .map(_.map(_.content))
          )
        )
      )
    )
}
