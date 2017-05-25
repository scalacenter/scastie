package com.olegych.scastie
package web
package routes

import api._
import balancer._

import de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._

import akka.util.Timeout
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask

import akka.http.scaladsl._
import server.Directives._

import akka.stream.scaladsl._

import upickle.default.{write => uwrite}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

class StatusRoutes(statusActor: ActorRef) {

  implicit val timeout = Timeout(1.seconds)

  val routes =
    path("status-sse")(
      complete(
        statusSource().map(progress =>
          ServerSentEvent(uwrite(progress))
        )
      )
    )

  private def statusSource() = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (statusActor ? SubscribeStatus)
        .mapTo[Source[StatusProgress, NotUsed]],
      1.second
    )
  }
}
