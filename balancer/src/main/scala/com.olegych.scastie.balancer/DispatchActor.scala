package com.olegych.scastie
package balancer

import api._

import akka.actor.{Actor, ActorRef, ActorSelection, ActorLogging}
import akka.remote.DisassociatedEvent

import com.typesafe.config.ConfigFactory

import java.nio.file._

import scala.collection.immutable.Queue

case class Address(host: String, port: Int)
case class SbtConfig(config: String)

case class RunSnippet(inputs: Inputs, ip: String, user: Option[User])

case class CreateSnippet(inputs: Inputs, user: Option[User])

case class AmendSnippet(snippetId: SnippetId, inputs: Inputs)
case class UpdateSnippet(snippetId: SnippetId, inputs: Inputs)
case class DeleteSnippet(snippetId: SnippetId)

case class ForkSnippet(snippetId: SnippetId, user: Option[User])

case class FetchSnippet(snippetId: SnippetId)
case class FetchUserSnippets(user: User)

case object LoadBalancerStateRequest
case class LoadBalancerStateResponse(
    loadBalancer: LoadBalancer[String, ActorSelection])

class DispatchActor(progressActor: ActorRef) extends Actor with ActorLogging {

  private val configuration =
    ConfigFactory.load().getConfig("com.olegych.scastie.balancer")

  private val portsStart = configuration.getInt("remote-ports-start")
  private val portsSize = configuration.getInt("remote-ports-size")
  private val host = configuration.getString("remote-hostname")

  private val ports = (0 until portsSize).map(portsStart + _)

  private var remoteSelections = ports.map { port =>
    val selection = context.actorSelection(
      s"akka.tcp://SbtRemote@$host:$port/user/SbtActor"
    )
    (host, port) -> selection
  }.toMap

  private var loadBalancer: LoadBalancer[String, ActorSelection] = {
    val servers = remoteSelections.to[Vector].map {
      case (_, ref) =>
        Server(ref, Inputs.default.sbtConfig)
    }

    val history = History(Queue.empty[Record[String]], size = 100)
    LoadBalancer(servers, history)
  }

  override def preStart = {
    context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
    ()
  }

  private val container = new SnippetsContainer(
    Paths.get(configuration.getString("snippets-dir"))
  )

  private val portsInfo = ports.mkString("[", ", ", "]")
  log.info(s"connecting to: $host $portsInfo")

  def receive = {
    case RunSnippet(inputs, ip, user) => {
      val login = user.map(_.login)
      log.info("login: {}, ip: {} run {}", login, ip, inputs)

      val snippetId = container.create(inputs, user)

      val (server, balancer) =
        loadBalancer.add(Task(inputs.sbtConfig, Ip(ip), snippetId))
      loadBalancer = balancer
      server.ref.tell(SbtTask(snippetId, inputs, ip, login, progressActor), self)
      sender ! snippetId
    }

    case format: FormatRequest => {
      val server = loadBalancer.getRandomServer
      server.ref.tell(format, sender)
      ()
    }

    case CreateSnippet(inputs, user) => {
      sender ! container.create(inputs, user)
    }

    case AmendSnippet(snippetId, inputs) => {
      sender ! container.amend(snippetId, inputs)
    }

    case UpdateSnippet(snippetId, inputs) => {
      sender ! container.update(snippetId, inputs)
    }

    case DeleteSnippet(snippetId) => {
      container.delete(snippetId)
      sender ! (())
    }

    case ForkSnippet(snippetId, maybeUser) => {
      sender ! container.fork(snippetId, maybeUser)
    }

    case FetchSnippet(snippetId) => {
      sender ! container.readSnippet(snippetId)
    }

    case FetchUserSnippets(user) => {
      sender ! container.listSnippets(user.login)
    }

    case progress: api.SnippetProgress => {
      if (progress.done) {
        loadBalancer = loadBalancer.done(progress.snippetId)
      }
      container.appendOutput(progress)
    }

    case event: DisassociatedEvent => {
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteSelections.get((host, port))
      } {
        log.warning(event.toString)
        log.warning("removing disconnected: " + ref)
        remoteSelections = remoteSelections - ((host, port))
        loadBalancer = loadBalancer.removeServer(ref)
      }
    }

    case LoadBalancerStateRequest => {
      sender ! LoadBalancerStateResponse(loadBalancer)
    }
  }
}
