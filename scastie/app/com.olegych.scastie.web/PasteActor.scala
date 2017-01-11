package com.olegych.scastie
package web

import api._
import remote.{RunPaste, PasteProgress}

import play.api.Play
import play.api.Play.current

import akka.actor.{Actor, ActorRef}
import akka.routing.{ActorSelectionRoutee, RoundRobinRoutingLogic, Router}

import java.nio.file._

class PasteActor(progressActor: ActorRef) extends Actor {

  private val container = new PastesContainer(
    Paths.get(Play.configuration.getString("pastes.data.dir").get)
  )

  private val router = {
    val routees =
      Vector(
        ActorSelectionRoutee(
          context.actorSelection(
            s"akka.tcp://SbtRemote@127.0.0.1:5150/user/SbtActor"
          )
        )
      )
    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case inputs: Inputs => {
      val id = container.writePaste(inputs)
      router.route((id, inputs, progressActor), self)
      sender ! id
    }

    case GetPaste(id) => {
      sender ! container.readPaste(id)
    }
  }
}

case class GetPaste(id: Long)
