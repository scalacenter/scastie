package com.olegych.scastie
package web

import akka.actor._

object WebSocketActor {
  def props(id: Int, out: ActorRef, progressActor: ActorRef) = 
    Props(new WebSocketActor(id, out, progressActor))
}

case class Subscribe(id: Int)

class WebSocketActor(id: Int, out: ActorRef, progressActor: ActorRef) extends Actor {

  override def preStart(): Unit =
    progressActor ! Subscribe(id)

  def receive = {
    case msg: String =>
      out ! ("I received your message: " + msg)
  }
}
