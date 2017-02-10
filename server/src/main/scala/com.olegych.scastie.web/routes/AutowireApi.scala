package com.olegych.scastie
package web
package routes

import api._


import akka.http.scaladsl._
import model.RemoteAddress
import server.Directives._

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

class ApiImpl(pasteActor: ActorRef, ip: RemoteAddress)(
  implicit timeout: Timeout, executionContext: ExecutionContext) extends Api {

  def run(inputs: Inputs): Future[Ressource] = {
    (pasteActor ? InputsWithIp(inputs, ip.toIP.map(_.ip.toString).getOrElse("-no-ip-"))).mapTo[Ressource]
  }

  def save(inputs: Inputs): Future[Ressource] = run(inputs)

  def fetch(id: Int): Future[Option[FetchResult]] = {
    (pasteActor ? GetPaste(id)).mapTo[Option[FetchResult]]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (pasteActor ? formatRequest).mapTo[FormatResponse]
  }
}

class AutowireApi(pasteActor: ActorRef)(implicit system: ActorSystem){
  import system.dispatcher

  implicit val timeout = Timeout(100.seconds)

  val routes = 
    post(
      path("api" / Segments)( s ⇒
        entity(as[String])( e ⇒
          extractClientIP( remoteAddress ⇒
            complete{
              val api = new ApiImpl(pasteActor, remoteAddress)
              AutowireServer.route[Api](api)(
                autowire.Core.Request(s, uread[Map[String, String]](e))
              )
            }
          )
        )
      )
    )
}
