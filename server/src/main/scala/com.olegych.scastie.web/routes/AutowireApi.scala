package com.olegych.scastie
package web
package routes

import api._
import balancer._

import akka.http.scaladsl._
import server.Directives._

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.duration._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

class AutowireApi(pasteActor: ActorRef)(implicit system: ActorSystem){
  import system.dispatcher

  implicit val timeout = Timeout(5.seconds)

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
