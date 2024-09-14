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

class ScalaCliDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, ServerState](config) {

  private val parent = context.parent

  val remoteServers = getRemoteServers("scli", "ScalaCliRunner", "ScalaCliActor")

  val availableServersQueue = new ConcurrentLinkedQueue[(SocketAddress, ActorSelection)](remoteServers.toSeq.asJava)
  val taskQueue = new ConcurrentLinkedQueue[Task[ScalaCliInputs]]()
  val processedSnippetsId: TrieMap[SnippetId, (SocketAddress, ActorSelection)] = TrieMap.empty

  private def run0(scalaCliInputs: ScalaCliInputs, userTrace: UserTrace, snippetId: SnippetId) = {
    val UserTrace(ip, user) = userTrace

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, scalaCliInputs)

    val task = Task[ScalaCliInputs](scalaCliInputs, Ip(ip), TaskId(snippetId), Instant.now)
    taskQueue.add(task)

    giveTask()
  }

  private def enqueueAvailableServer(addr: SocketAddress, server: ActorSelection) =
    if (remoteServers.contains(addr)) {
      availableServersQueue.add(addr, server)
      giveTask()
    }


  private def giveTask() = {
    val maybeTask = Option(taskQueue.poll())
    maybeTask.map { task =>
      Option(availableServersQueue.poll) match {
        case None => ()
        case Some((addr, server)) => {
          log.info(s"Giving task ${task.taskId} to ${server.pathString}")
          server ! ScalaCliActorTask(task.taskId.snippetId, task.config.asInstanceOf[ScalaCliInputs], task.ip.v, progressActor)
          processedSnippetsId.addOne(task.taskId.snippetId, (addr, server))
        }
      }
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
        enqueueAvailableServer(address, ref)
        giveTask()
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
        val (addr, server) = processedSnippetsId(sid)
        log.info(s"Runner $addr has finished processing $sid.")
        processedSnippetsId.remove(sid)
        enqueueAvailableServer(addr, server)
      }

    case Run(InputsWithIpAndUser(scalaCliInputs: ScalaCliInputs, userTrace), snippetId) =>
      run0(scalaCliInputs, userTrace, snippetId)

    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteServers.get(SocketAddress(host, port))
      } {
        log.warning("removing disconnected: {}", ref)
        remoteServers.remove(SocketAddress(host, port))
      }

    case _ => ()
  }
}
