package com.olegych.scastie
package web

import api._
import remote._

import scala.collection.JavaConverters._

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
    val ports = Play.configuration.getIntList("sbt-remote-ports").get.asScala
    val host = Play.configuration.getString("sbt-remote-host").get

    val routees =
      ports.map(port =>
        ActorSelectionRoutee(
          context.actorSelection(
            s"akka.tcp://SbtRemote@$host:$port/user/SbtActor"
          )
        )
      ).toVector

    // routees.foreach(context.watch)

    Router(RoundRobinRoutingLogic(), routees)
  }

  def receive = {
    case inputs: Inputs => {
      val id = container.writePaste(inputs)
      router.route((id, inputs, progressActor), self)
      sender ! id
    }

    case GetPaste(id) => {
      sender !
        container.readPaste(id).zip(container.readOutput(id)).headOption.map {
          case (inputs, progresses) =>
            FetchResult(inputs, progresses)
        }
    }

    case progress: api.PasteProgress => {
      container.appendOutput(progress)
    }
  }
}

case class GetPaste(id: Long)
