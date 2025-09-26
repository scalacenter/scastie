package org.scastie.balancer

import akka.event
import akka.actor.Actor
import akka.actor.ActorRef
import com.typesafe.config.Config
import akka.actor.ActorLogging
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection
import org.scastie.api._
import org.scastie.util._
import scala.concurrent.Future
import akka.remote.DisassociatedEvent
import java.time.Instant
import scala.concurrent._
import akka.pattern.ask

import scala.concurrent.duration._
import java.util.concurrent.Executors
import akka.actor.Address
import akka.actor.ActorSystem
import akka.util.Timeout
import java.util.concurrent.atomic.AtomicReference

class SbtDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, ServerState](config) with Actor {

  private val parent = context.parent

  val remoteSbtSelections = getRemoteServers("sbt", "SbtRunner", "SbtActor")

  val balancer: AtomicReference[SbtBalancer] = {
    val sbtServers = remoteSbtSelections.to(Vector).map {
      case (_, ref) =>
        val state: ServerState = ServerState.Unknown
        SbtServer(ref, SbtInputs.default, state)
    }

    new AtomicReference(LoadBalancer(servers = sbtServers))
  }

  private def updateSbtBalancer(newBalancer: SbtBalancer): Unit = {
    if (balancer != newBalancer) {
      statusActor ! SbtLoadBalancerUpdate(newBalancer)
    }
    balancer.set(newBalancer)
    ()
  }

  import context._

  // cannot be called from future
  private def run0(sbtInputs: SbtInputs, userTrace: UserTrace, snippetId: SnippetId): Unit = {

    val UserTrace(ip, user) = userTrace

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, sbtInputs)

    val task = Task(sbtInputs, Ip(ip), TaskId(snippetId), Instant.now)

    balancer.get.add(task) match {
      case Some((server, newBalancer)) =>
        updateSbtBalancer(newBalancer)

        server.ref.tell(
          SbtTask(snippetId, sbtInputs, ip, user.map(_.login), progressActor),
          self
        )
      case _ => ()
    }
  }

  def receive: Receive = event.LoggingReceive(event.Logging.InfoLevel) {
    case progress: SnippetProgress =>
      implicit val timeout: Timeout = Timeout(10.seconds)
      val sender = this.sender()
      if (progress.isDone) {
        self ! Done(progress, retries = 100)
      }
      (parent ? progress).map(sender ! _)

    case done: Done =>
      done.progress.snippetId.foreach { sid =>
        val newBalancer = balancer.get.done(TaskId(sid))
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
      val server = balancer.get.getRandomServer
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
        remoteSbtSelections.remove(SocketAddress(host, port))
        if (previousRemoteSbtSelections != remoteSbtSelections) {
          updateSbtBalancer(balancer.get.removeServer(ref))
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

        remoteSbtSelections.addOne(sel)

        val state: ServerState = ServerState.Unknown

        updateSbtBalancer(
          balancer.get.addServer(
            SbtServer(ref, SbtInputs.default, state)
          )
        )
      }

    case ReceiveStatus(requester) =>
      sender() ! LoadBalancerInfo(balancer.get, requester)

    case Run(InputsWithIpAndUser(sbtTask: SbtInputs, userTrace), snippetId) =>
      run0(sbtTask, userTrace, snippetId)

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
