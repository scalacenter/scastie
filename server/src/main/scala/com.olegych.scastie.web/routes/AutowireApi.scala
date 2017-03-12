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
import server.{Route, PathMatcher}

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
  import userDirectives.userLogin //optionnalLogin

  implicit val timeout = Timeout(1.seconds)

  private def snippetId(pathStart: String)(f: SnippetId => Route): Route =
    snippetIdBase(pathStart, p => p, p => p)(f)

  private def snippetIdEnd(pathStart: String, pathEnd: String)(f: SnippetId => Route): Route =
    snippetIdBase(pathStart, _ / pathEnd, _ / pathEnd)(f)

  private def snippetIdBase(pathStart: String, 
      fp1: PathMatcher[Tuple1[String]] => PathMatcher[Tuple1[String]],
      fp2: PathMatcher[(String, String, Option[Int])] => PathMatcher[(String, String, Option[Int])])(f: SnippetId => Route): Route = {
    concat(
      path(fp1(pathStart / Segment))(uuid ⇒
        f(SnippetId(uuid, None))
      ),
      path(fp2(pathStart / Segment / Segment / IntNumber.?))((user, uuid, update) ⇒
        f(SnippetId(uuid, Some(SnippetUserPart(user, update))))
      )
    )
  }

  val routes =
    concat(
      post(
        path("api" / Segments)(s ⇒
          entity(as[String])(e ⇒
            extractClientIP(remoteAddress ⇒
              userLogin( user ⇒
                complete {
                  val api = new ApiImplementation(dispatchActor, remoteAddress, Some(user))
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
            onSuccess((dispatchActor ? LoadBalancerStateRequest).mapTo[LoadBalancerStateResponse])(state =>
              complete(
                serveStatic(getResource("/public/views/loadbalancer.html").map(_.replaceAllLiterally(
                  "==STATE==",
                  state.loadBalancer.debug
                )))
              )
            )
          ),
          snippetId("progress-sse")(sid ⇒
            complete(
              progressSource(sid).map(progress => ServerSentEvent(uwrite(progress)))
            )
          ),
          snippetId("progress-websocket")(sid =>
            handleWebSocketMessages(webSocketProgress(sid))
          ),
          snippetIdEnd(Shared.scalaJsHttpPathPrefix, ScalaTarget.Js.targetFilename)(sid =>
            complete(
              (dispatchActor ? FetchScalaJs(sid))
                .mapTo[Option[FetchResultScalaJs]]
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
    )

  private def progressSource(snippetId: SnippetId) = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (progressActor ? SubscribeProgress(snippetId)).mapTo[Source[SnippetProgress, NotUsed]],
      1.second
    )
  }

  private def webSocketProgress(snippetId: SnippetId): Flow[ws.Message, ws.Message , _] = {
    def flow: Flow[KeepAlive, SnippetProgress, NotUsed] = {
      val in = Flow[KeepAlive].to(Sink.ignore)
      val out = progressSource(snippetId)
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
