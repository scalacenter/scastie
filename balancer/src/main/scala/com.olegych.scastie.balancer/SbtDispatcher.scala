package com.olegych.scastie.balancer

import akka.event
import akka.actor.Actor
import akka.actor.ActorRef
import com.typesafe.config.Config
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection
import com.olegych.scastie.api
import com.olegych.scastie.api._
import com.olegych.scastie.util._
import scala.concurrent.Future
import akka.remote.DisassociatedEvent
import com.olegych.scastie.api.SnippetId
import java.time.Instant
import com.olegych.scastie.api.TaskId
import scala.concurrent._
import akka.pattern.ask

import scala.concurrent.duration._
import java.util.concurrent.Executors
import akka.actor.Address
import akka.actor.ActorSystem
import akka.util.Timeout

class SbtDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, SbtState](config) with Actor {

  private val parent = context.parent

  var remoteSbtSelections = getRemoteServers("sbt", "SbtRunner", "SbtActor")

  var balancer: SbtBalancer = {
    val sbtServers = remoteSbtSelections.to(Vector).map {
      case (_, ref) =>
        val state: SbtState = SbtState.Unknown
        Server(ref, Inputs.default, state)
    }

    LoadBalancer(servers = sbtServers)
  }

  private def updateSbtBalancer(newBalancer: SbtBalancer): Unit = {
    if (balancer != newBalancer) {
      statusActor ! SbtLoadBalancerUpdate(newBalancer)
    }
    balancer = newBalancer
    ()
  }

  import context._

  // cannot be called from future
  private def run0(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId): Unit = {

    val InputsWithIpAndUser(inputs, UserTrace(ip, user)) = inputsWithIpAndUser

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, inputs)

    val task = Task(inputs, Ip(ip), TaskId(snippetId), Instant.now)

    balancer.add(task) match {
      case Some((server, newBalancer)) =>
        updateSbtBalancer(newBalancer)

        server.ref.tell(
          SbtTask(snippetId, inputs, ip, user.map(_.login), progressActor),
          self
        )
      case _ => ()
    }
  }

  def receive: Receive = event.LoggingReceive(event.Logging.InfoLevel) {
    case progress: api.SnippetProgress =>
      implicit val timeout: Timeout = Timeout(10.seconds)
      val sender = this.sender()
      if (progress.isDone) {
        self ! Done(progress, retries = 100)
      }
      (parent ? progress).map(sender ! _)

    case done: Done =>
      done.progress.snippetId.foreach { sid =>
        val newBalancer = balancer.done(TaskId(sid))
        newBalancer match {
          case Some(newBalancer) =>
            updateSbtBalancer(newBalancer)
          case None => () // never happens
        }
      }

    case RunnerPong => ()

    case SbtUp =>
      log.info("SbtUp")
    
    case format: FormatRequest =>
      val server = balancer.getRandomServer
      server.foreach(_.ref.tell(format, sender()))
      ()


    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteSbtSelections.get(SocketAddress(host, port))
      } {
        log.warning("removing disconnected: {}", ref)
        val previousRemoteSbtSelections = remoteSbtSelections
        remoteSbtSelections = remoteSbtSelections - (SocketAddress(host, port))
        if (previousRemoteSbtSelections != remoteSbtSelections) {
          updateSbtBalancer(balancer.removeServer(ref))
        }
      }
    
    case Replay(SbtRun(snippetId, inputs, progressActor, snippetActor)) =>
      log.info("Replay: " + inputs.code)

    case RunnerConnect(runnerHostname, runnerAkkaPort) =>
      if (!remoteSbtSelections.contains(SocketAddress(runnerHostname, runnerAkkaPort))) {
        log.info("Connected Runner {}", runnerAkkaPort)

        val address = SocketAddress(runnerHostname, runnerAkkaPort)
        val ref = connectRunner(getRemoteActorPath("SbtRunner", address, "SbtActor"))
        val sel = SocketAddress(runnerHostname, runnerAkkaPort) -> ref

        remoteSbtSelections = remoteSbtSelections + sel

        val state: SbtState = SbtState.Unknown

        updateSbtBalancer(
          balancer.addServer(
            Server(ref, Inputs.default, state)
          )
        )
      }

    case ReceiveStatus(requester) =>
      sender() ! LoadBalancerInfo(balancer, requester)

    case run: Run =>
      run0(run.inputsWithIpAndUser, run.snippetId)

    case p: Ping.type =>
      val sender = this.sender()
      ping(remoteSbtSelections.values.toList).andThen(s => sender ! RunnerPong)
  }

  private def logError[T](f: Future[T]) = {
    f.recover {
      case e => log.error(e, "failed future")
    }
  }
}
