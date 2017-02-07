package com.olegych.scastie
package web

import api._

import scala.collection.JavaConverters._

import play.api.Play
import play.api.Play.{current, configuration}

import akka.actor.{Actor, ActorRef}
import akka.remote.DisassociatedEvent
import akka.routing.{ActorSelectionRoutee, RoundRobinRoutingLogic, Router}

import java.nio.file._
import org.slf4j.LoggerFactory

import scala.collection.mutable.{Map => MMap}

import scala.collection.immutable.Queue

case class Address(host: String, port: Int)
case class SbtConfig(config: String)
case class InputsWithIp(inputs: Inputs, ip: String)

case class GetPaste(id: Int)

class PasteActor(progressActor: ActorRef) extends Actor {

  private val portsStart = configuration.getInt("sbt-remote-ports-start").get
  private val portsSize = configuration.getInt("sbt-remote-ports-size").get
  private val host = configuration.getString("sbt-remote-hostname").get

  private val ports = (0 until portsSize).map(portsStart + _)

  private var remoteSelections = ports.map{port =>
    val selection = context.actorSelection(
      s"akka.tcp://SbtRemote@$host:$port/user/SbtActor"
    )
    (host, port) -> selection
  }.toMap

  private var loadBalancer = {
    val servers = remoteSelections.to[Vector].map{ case (_, ref) =>
      Server(ref, Inputs.default.sbtConfig)
    }

    val history = History(Queue.empty[Record[String]], size = 100)
    LoadBalancer(servers, history)
  }

  private val log = LoggerFactory.getLogger(getClass)

  override def preStart = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    ()
  }

  private val container = new PastesContainer(
    Paths.get(configuration.getString("pastes.data.dir").get)
  )

  private val portsInfo = ports.mkString("[", ", ", "]")
  log.info(s"connecting to: $host $portsInfo")

  def receive = {
    case format: FormatRequest => {
      val server = loadBalancer.getRandomServer
      server.ref.tell(format, sender)
      ()
    }
    case InputsWithIp(inputs, ip) => {
      val id = container.writePaste(inputs)

      val (server, balancer) = loadBalancer.add(Task(inputs.sbtConfig, Ip(ip), id))
      loadBalancer = balancer
      server.ref.tell(SbtTask(id, inputs, ip, progressActor), self)
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
      if(progress.done) {
        loadBalancer = loadBalancer.done(progress.id)
      }
      container.appendOutput(progress)
    }

    case event: DisassociatedEvent => {
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref  <- remoteSelections.get((host, port))
      } {
        log.warn(event.toString)
        log.warn("removing disconnected: " + ref)
        remoteSelections = remoteSelections - ((host, port))
        loadBalancer.removeServer(ref)
      }
    }
  }
}
