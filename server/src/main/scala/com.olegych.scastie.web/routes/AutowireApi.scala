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
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.Directives._

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer] {
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

class AutowireApi(pasteActor: ActorRef, progressActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  implicit val timeout = Timeout(1.seconds)

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
        path("progress" / Segment)(id ⇒
            complete{
              val source = Await.result(
                (progressActor ? SubscribeProgress(id)).mapTo[Source[PasteProgress, NotUsed]],
                1.second
              )
              source.map(progress => ServerSentEvent(uwrite(progress)))
            }
        )
      )
    )

  private def timeToServerSentEvent(time: LocalTime) =
    ServerSentEvent(DateTimeFormatter.ISO_LOCAL_TIME.format(time))
}
