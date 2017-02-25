package com.olegych.scastie
package web
package routes

import api._
import balancer._
import oauth2._

import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.EventStreamMarshalling._

import akka.NotUsed
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import akka.http.scaladsl._
import model._
import ws.TextMessage._
import server.Directives._

import akka.stream.scaladsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer] {
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

class AutowireApi(dispatchActor: ActorRef, progressActor: ActorRef, userDirectives: UserDirectives)(implicit system: ActorSystem) {
  import system.dispatcher
  import userDirectives.userLogin

  implicit val timeout = Timeout(1.seconds)

  val routes =
    concat(
      post(
        path("api" / Segments)(s ⇒
          entity(as[String])(e ⇒
            extractClientIP(remoteAddress ⇒
              userLogin( user ⇒
                complete {
                  val api = new ApiImplementation(dispatchActor, remoteAddress, user)
                  AutowireServer.route[Api](api)(
                    autowire.Core.Request(s, uread[Map[String, String]](e))
                  )
                }
              )
            )
          )
        )
      ),
      get(
        concat(
          path("loadbalancer-debug")(
            onSuccess((dispatchActor ? LoadBalancerStateRequest)
              .mapTo[LoadBalancerStateResponse])(state =>

              complete(
                serveStatic(getResource("/public/views/loadbalancer.html").map(_.replaceAllLiterally(
                  "==STATE==",
                  state.loadBalancer.debug
                )))
              )
            )
          ),
          path("progress-sse" / Segment)(id ⇒
            complete{
              progressSource(id)
                .map(progress => ServerSentEvent(uwrite(progress)))
            }
          ),
          path("progress-websocket" / Segment)(id =>
            handleWebSocketMessages(webSocketProgress(id))
          )
        )
      )
    )

  private def progressSource(id: String) = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (progressActor ? SubscribeProgress(id)).mapTo[Source[SnippetProgress, NotUsed]],
      1.second
    )
  }

  private def webSocketProgress(id: String): Flow[ws.Message, ws.Message , _] = {
    def flow: Flow[KeepAlive, SnippetProgress, NotUsed] = {
      val in = Flow[KeepAlive].to(Sink.ignore)
      val out = progressSource(id)
      Flow.fromSinkAndSource(in, out)
    }

    Flow[ws.Message]
      .mapAsync(1){
        case Strict(c) ⇒ Future.successful(c)
        case e => Future.failed(new Exception(e.toString))
      }
      .map(uread[KeepAlive](_))
      .via(flow)
      .map(progress ⇒ ws.TextMessage.Strict(uwrite(progress)))
  }
}
