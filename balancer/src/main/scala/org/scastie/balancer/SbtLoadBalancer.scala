package org.scastie.balancer

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scastie.api._
import org.slf4j.LoggerFactory

import scala.util.Random

case class SbtLoadBalancer[R, S <: ServerState](servers: Vector[SbtServer[R, S]]) {
  private val log = LoggerFactory.getLogger(getClass)

  def done(taskId: TaskId): Option[SbtLoadBalancer[R, S]] = {
    Some(copy(servers = servers.map(_.done(taskId))))
  }

  def addServer(server: SbtServer[R, S]): SbtLoadBalancer[R, S] = {
    copy(servers = server +: servers)
  }

  def removeServer(ref: R): SbtLoadBalancer[R, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Option[SbtServer[R, S]] = {
    def random[T](xs: Seq[T]) = if (xs.nonEmpty) Some(xs(Random.nextInt(xs.size))) else None
    random(servers.filter(_.state.isReady))
  }

  def add(task: Task[SbtInputs]): Option[(SbtServer[R, S], SbtLoadBalancer[R, S])] = {
    log.info("Task added: {}", task.taskId)

    val (availableServers, unavailableServers) =
      servers.partition(_.state.isReady)

    def lastTenMinutes(v: Vector[Task[SbtInputs]]) = v.filter(_.ts.isAfter(Instant.now.minus(10, ChronoUnit.MINUTES)))
    def lastWithIp(v: Vector[Task[SbtInputs]]) = lastTenMinutes(v.filter(_.ip == task.ip)).lastOption

    if (availableServers.nonEmpty) {
      val selectedServer = availableServers.maxBy { s =>
        (
          s.mailbox.length < 3, //allow reload if server gets busy
          !s.configAfterMailbox.needsReload(task.config), //pick those without need for reload
          -s.mailbox.length, //then those least busy
          lastTenMinutes(s.mailbox ++ s.history.data).exists(!_.config.needsReload(task.config)), //then those which use(d) this config
          lastWithIp(s.mailbox).orElse(lastWithIp(s.history.data)).map(_.ts.toEpochMilli), //then one most recently used by this ip, if any
          s.mailbox.lastOption.orElse(s.history.data.lastOption).map(-_.ts.toEpochMilli).getOrElse(0L) //then one least recently used
        )
      }
      val updatedServers = availableServers.map(old => if (old.id == selectedServer.id) old.add(task) else old)
      Some(
        (
          selectedServer,
          copy(
            servers = updatedServers ++ unavailableServers,
//            history = updatedHistory
          )
        )
      )
    } else {
      if (servers.isEmpty) {
        val msg = "All instances are down"
        log.error(msg)
      }
      None
    }
  }

}
