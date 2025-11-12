package org.scastie.balancer

import akka.actor.Actor
import akka.actor.ActorLogging
import com.typesafe.config.Config
import akka.actor.ActorRef
import akka.actor.ActorSelection
import java.time.Instant
import scala.collection.immutable.Queue
import org.scastie.util.SbtTask
import org.scastie.util.ScalaCliActorTask
import org.scastie.api._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.remote.DisassociatedEvent
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._
import scala.collection.concurrent.TrieMap
import java.util.concurrent.atomic.AtomicReference

class ScalaCliDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, ServerState](config) {

  private val parent = context.parent

  val remoteServers = getRemoteServers("scli", "ScalaCliRunner", "ScalaCliActor")

  val balancer: AtomicReference[ScalaCliBalancer] = {
    val scalaCliServers = remoteServers.to(Vector).map {
      case (_, ref) =>
        val state: ServerState = ServerState.Unknown
        ScalaCliServer(ref, ScalaCliInputs.default, state)
    }

    new AtomicReference(ScalaCliLoadBalancer(servers = scalaCliServers))
  }

  private def updateScalaCliBalancer(newBalancer: ScalaCliBalancer): Unit = {
    if (balancer.get != newBalancer) {
      statusActor ! ScalaCliLoadBalancerUpdate(newBalancer)
    }
    balancer.set(newBalancer)
    ()
  }

  private def run0(scalaCliInputs: ScalaCliInputs, userTrace: UserTrace, snippetId: SnippetId) = {
    val UserTrace(ip, user) = userTrace

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, scalaCliInputs)

    val task = Task[ScalaCliInputs](scalaCliInputs, Ip(ip), TaskId(snippetId), Instant.now)

    balancer.get.add(task) match {
      case Some((server, newBalancer)) =>
        updateScalaCliBalancer(newBalancer)

        server.ref.tell(
          ScalaCliActorTask(snippetId, scalaCliInputs, ip, progressActor),
          self
        )
      case _ => ()
    }
  }

  import context._

  def receive: Receive = {
    case RunnerPong => ()

    case p: Ping.type =>
      val sender = this.sender()
      ping(remoteServers.values.toList).andThen(s => sender ! RunnerPong)

    case RunnerConnect(runnerHostname, runnerAkkaPort) =>
      if (!remoteServers.contains(SocketAddress(runnerHostname, runnerAkkaPort))) {
        log.info("Connected runner {}", runnerAkkaPort)

        val address = SocketAddress(runnerHostname, runnerAkkaPort)
        val ref = connectRunner(getRemoteActorPath("ScalaCliRunner", address, "ScalaCliActor"))

        remoteServers.addOne(address -> ref)

        val state: ServerState = ServerState.Unknown

        updateScalaCliBalancer(
          balancer.get.addServer(
            ScalaCliServer(ref, ScalaCliInputs.default, state)
          )
        )
      }

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
            updateScalaCliBalancer(newBalancer)
          case None => ()
        }
      }

    case Run(InputsWithIpAndUser(scalaCliInputs: ScalaCliInputs, userTrace), snippetId) =>
      run0(scalaCliInputs, userTrace, snippetId)

    case ReceiveStatus(requester) =>
      sender() ! ScalaCliLoadBalancerInfo(balancer.get, requester)

    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteServers.get(SocketAddress(host, port))
      } {
        log.warning("removing disconnected: {}", ref)
        val previousRemoteServers = remoteServers
        remoteServers.remove(SocketAddress(host, port))
        if (previousRemoteServers != remoteServers) {
          updateScalaCliBalancer(balancer.get.removeServer(ref))
        }
      }

    case _ => ()
  }
}
