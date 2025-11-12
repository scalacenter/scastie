package org.scastie.balancer

import java.time.Instant

import org.scastie.api._
import org.slf4j.LoggerFactory

import scala.util.Random

case class ScalaCliLoadBalancer[R, S <: ServerState](servers: Vector[ScalaCliServer[R, S]]) {
  private val log = LoggerFactory.getLogger(getClass)

  def done(taskId: TaskId): Option[ScalaCliLoadBalancer[R, S]] = {
    Some(copy(servers = servers.map(_.done(taskId))))
  }

  def addServer(server: ScalaCliServer[R, S]): ScalaCliLoadBalancer[R, S] = {
    copy(servers = server +: servers)
  }

  def removeServer(ref: R): ScalaCliLoadBalancer[R, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Option[ScalaCliServer[R, S]] = {
    def random[T](xs: Seq[T]) = if (xs.nonEmpty) Some(xs(Random.nextInt(xs.size))) else None
    random(servers.filter(_.state.isReady))
  }

  def add(task: Task[ScalaCliInputs]): Option[(ScalaCliServer[R, S], ScalaCliLoadBalancer[R, S])] = {
    log.info("Task added: {}", task.taskId)

    val (availableServers, unavailableServers) =
      servers.partition(_.state.isReady)

    if (availableServers.nonEmpty) {
      val selectedServer = availableServers.minBy(_.mailbox.length)
      val updatedServers = availableServers.map(old => if (old.id == selectedServer.id) old.add(task) else old)
      Some(
        (
          selectedServer,
          copy(servers = updatedServers ++ unavailableServers)
        )
      )
    } else {
      if (servers.isEmpty) {
        val msg = "All Scala-CLI instances are down"
        log.error(msg)
      }
      None
    }
  }
}
