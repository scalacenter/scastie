package org.scastie.balancer

import org.apache.pekko.event
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import com.typesafe.config.Config
import org.apache.pekko.actor.ActorLogging
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSelection
import org.scastie.api._
import org.scastie.util._
import scala.concurrent.Future
import org.apache.pekko.remote.DisassociatedEvent
import java.time.Instant
import scala.concurrent._
import org.apache.pekko.pattern.ask

import scala.concurrent.duration._
import java.util.concurrent.Executors
import org.apache.pekko.actor.Address
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.Timeout
import java.util.concurrent.atomic.AtomicReference

class SbtDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, ServerState](config) with Actor {

  private val parent = context.parent

  val remoteSbtSelections = getRemoteServers("sbt", "SbtRunner", "SbtActor")

  val balancer: AtomicReference[SbtBalancer] = {
    val sbtServers = remoteSbtSelections.to(Vector).map {
      case (_, ref) =>
        val state: ServerState = ServerState.Unknown
        Server(ref, SbtInputs.default, state)
    }

    new AtomicReference(SbtLoadBalancer(servers = sbtServers))
  }

  private def updateSbtBalancer(newBalancer: SbtBalancer): Unit = {
    val oldBalancer = balancer.get
    if (oldBalancer != newBalancer) {
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

    case RunnerConnect(runnerHostname, runnerPekkoPort) =>
      if (!remoteSbtSelections.contains(SocketAddress(runnerHostname, runnerPekkoPort))) {
        log.info("Connected Runner {}", runnerPekkoPort)

        val address = SocketAddress(runnerHostname, runnerPekkoPort)
        val ref = connectRunner(getRemoteActorPath("SbtRunner", address, "SbtActor"))
        val sel = SocketAddress(runnerHostname, runnerPekkoPort) -> ref

        remoteSbtSelections.addOne(sel)

        val state: ServerState = ServerState.Unknown

        updateSbtBalancer(
          balancer.get.addServer(
            Server(ref, SbtInputs.default, state)
          )
        )
      }

    case ReceiveStatus(requester) =>
      sender() ! SbtLoadBalancerInfo(balancer.get, requester)

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
