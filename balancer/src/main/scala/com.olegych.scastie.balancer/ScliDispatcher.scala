package com.olegych.scastie.balancer

import akka.actor.Actor
import akka.actor.ActorLogging
import com.typesafe.config.Config
import akka.actor.ActorRef
import akka.actor.ActorSelection
import com.olegych.scastie.api.ActorConnected
import com.olegych.scastie.api.ScliState
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.balancer.Ping
import com.olegych.scastie.api.RunnerConnect
import com.olegych.scastie.api.RunnerPong
import com.olegych.scastie.api.TaskId
import java.time.Instant
import scala.collection.immutable.Queue
import com.olegych.scastie.util.SbtTask
import com.olegych.scastie.util.ScliActorTask
import com.olegych.scastie.api.SnippetProgress
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.remote.DisassociatedEvent

class ScliDispatcher(config: Config, progressActor: ActorRef, statusActor: ActorRef)
  extends BaseDispatcher[ActorSelection, ScliState](config) {

  private val parent = context.parent

  var remoteServers = getRemoteServers("scli", "ScliRunner", "ScliActor")

  var availableServersQueue = Queue[(SocketAddress, ActorSelection)](remoteServers.toSeq :_ *)
  var taskQueue = Queue[Task]()
  var processedSnippetsId: Map[SnippetId, (SocketAddress, ActorSelection)] = Nil.toMap

  private def run0(inputsWithIpAndUser: InputsWithIpAndUser, snippetId: SnippetId) = {
    val InputsWithIpAndUser(inputs, UserTrace(ip, user)) = inputsWithIpAndUser

    log.info("id: {}, ip: {} run inputs: {}", snippetId, ip, inputs)

    val task = Task(inputs, Ip(ip), TaskId(snippetId), Instant.now)

    taskQueue = taskQueue.enqueue(task)

    giveTask
  }

  private def enqueueAvailableServer(addr: SocketAddress, server: ActorSelection) =
    if (remoteServers.contains(addr))
      availableServersQueue = availableServersQueue.enqueue((addr, server))
      giveTask

  private def giveTask = {
    if (!taskQueue.isEmpty) {
      val (task, newTaskQueue) = taskQueue.dequeue

      availableServersQueue.dequeueOption match {
        case None => ()
        case Some(((addr, server), newQueue)) => {
          log.info(s"Giving task ${task.taskId} to ${server.pathString}")
          taskQueue = newTaskQueue
          availableServersQueue = newQueue
          server ! ScliActorTask(task.taskId.snippetId, task.config, task.ip.v, progressActor)
          processedSnippetsId += task.taskId.snippetId -> (addr, server)
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
        val ref = connectRunner(getRemoteActorPath("ScliRunner", address, "ScliActor"))

        remoteServers += address -> ref
        enqueueAvailableServer(address, ref)
        giveTask
      }

    case progress: SnippetProgress =>
      implicit val timeout: Timeout = Timeout(10.seconds)
      val sender = this.sender()
      if (progress.isDone) {
        self ! Done(progress, retries = 100)
      }
      (parent ? progress).map(answerOfProgress => {
        log.info(s"GOT ANSWER OF PROGRESS $answerOfProgress")
        sender ! answerOfProgress
      })

    case done: Done =>
      done.progress.snippetId.foreach { sid =>
        val (addr, server) = processedSnippetsId(sid)
        log.info(s"Runner $addr has finished processing $sid.")
        processedSnippetsId -= sid
        enqueueAvailableServer(addr, server)
      }

    case Run(inputsWithIpAndUser, snippetId) => run0(inputsWithIpAndUser, snippetId)

    case event: DisassociatedEvent =>
      for {
        host <- event.remoteAddress.host
        port <- event.remoteAddress.port
        ref <- remoteServers.get(SocketAddress(host, port))
      } {
        log.warning("removing disconnected: {}", ref)
        remoteServers = remoteServers - (SocketAddress(host, port))
      }

    case _ => ()
  }
}
