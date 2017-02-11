package com.olegych.scastie
package web
package routes

import api._
import balancer._

import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.EventStreamMarshalling._

import akka.NotUsed
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.Directives._


import java.time.LocalTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer] {
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}


class AutowireApi(pasteActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  val routes =
    concat(
      post(
        path("api" / Segments)(s ⇒
          entity(as[String])(e ⇒
            extractClientIP(remoteAddress ⇒
              complete {
                val api = new ApiImpl(pasteActor, remoteAddress)
                AutowireServer.route[Api](api)(
                  autowire.Core.Request(s, uread[Map[String, String]](e))
                )
            })))
      ),
      get(
        path("progress" / Segments)(
          id ⇒
            complete(
              Source
                .tick(2.seconds, 2.seconds, NotUsed)
                .map(_ => LocalTime.now())
                .map(timeToServerSentEvent)
                .keepAlive(1.second, () => ServerSentEvent.heartbeat))
            )
      )
    )

  private def timeToServerSentEvent(time: LocalTime) =
    ServerSentEvent(DateTimeFormatter.ISO_LOCAL_TIME.format(time))
}
