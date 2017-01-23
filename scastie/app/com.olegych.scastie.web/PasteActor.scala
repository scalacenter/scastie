package com.olegych.scastie
package web

import api._

import scala.collection.JavaConverters._

import play.api.Play
import play.api.Play.current

import akka.actor.{Actor, ActorRef}
import akka.remote.DisassociatedEvent
import akka.routing.{ActorSelectionRoutee, RoundRobinRoutingLogic, Router}

import java.nio.file._
import org.slf4j.LoggerFactory

class PasteActor(progressActor: ActorRef) extends Actor {

  private val log = LoggerFactory.getLogger(getClass)

  override def preStart = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    ()
  }

  private val container = new PastesContainer(
    Paths.get(Play.configuration.getString("pastes.data.dir").get)
  )

  private val ports =
    Play.configuration.getIntList("sbt-remote-ports").get.asScala
  private val host = Play.configuration.getString("sbt-remote-host").get

  val portsInfo = ports.mkString("[", ", ", "]")

  log.info(s"connecting to: $host $portsInfo")

  private var routees =
    ports
      .map(
        port =>
          (host, port) -> ActorSelectionRoutee(
            context.actorSelection(
              s"akka.tcp://SbtRemote@$host:$port/user/SbtActor"
            )
        ))
      .toMap

  private var router =
    Router(RoundRobinRoutingLogic(), routees.values.toVector)

  def receive = {
    case format: FormatRequest => {
      router.route(format, sender)
    }
    case inputs: Inputs => {
      val id = container.writePaste(inputs)
      router.route(SbtTask(id, inputs, progressActor), self)
      sender ! Ressource(id)
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

    case event: DisassociatedEvent => {
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        selection <- routees.get((host, port))
      } {
        log.warn("removing disconnected: " + selection)
        routees = routees - ((host, port))
        router = router.removeRoutee(selection)
      }
    }
  }
}

case class GetPaste(id: Long)
