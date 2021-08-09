package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import com.olegych.scastie.balancer.DispatchActor.Adapter.{
  FetchScalaJs, FetchScalaSource, FetchScalaJsSourceMap
}
import akka.util.Timeout
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.coding.Coders.Gzip
import com.olegych.scastie.balancer.DispatchActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

//not used anymore
class ScalaJsRoutes(dispatchActor: ActorRef[DispatchActor.Message]
)(implicit ec: ExecutionContext, scheduler: Scheduler) {

  implicit val timeout: Timeout = Timeout(1.seconds)

  val routes: Route =
    encodeResponseWith(Gzip)(
      concat(
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.targetFilename)(
          sid =>
            complete(
              dispatchActor
                .ask(FetchScalaJs(_, sid))
                .map(_.map(_.content))
          )
        ),
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.sourceFilename)(
          sid =>
            complete(
              dispatchActor
                .ask(FetchScalaSource(_, sid))
                .map(_.map(_.content))
          )
        ),
        snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.sourceMapFilename)(
          sid =>
            complete(
              dispatchActor
                .ask(FetchScalaJsSourceMap(_, sid))
                .mapTo[Option[FetchResultScalaJsSourceMap]]
                .map(_.map(_.content))
          )
        )
      )
    )
}
