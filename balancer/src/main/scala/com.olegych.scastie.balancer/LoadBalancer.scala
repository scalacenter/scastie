package com.olegych.scastie.balancer

import com.olegych.scastie.api._
import com.olegych.scastie.balancer.utils.Histogram
import org.slf4j.LoggerFactory

import scala.collection.immutable.Queue
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.util.Random

case class Ip(v: String)
case class Record(config: Inputs, ip: Ip)

case class Task(config: Inputs, ip: Ip, taskId: TaskId) {
  def toRecord: Record = Record(config, ip)
}

case class History(data: Queue[Record], size: Int) {
  def add(record: Record): History = {
    // the user has changed configuration, we assume he will not go back to the previous configuration
    val data0 = data.filterNot(_.ip == record.ip).enqueue(record)
    val data1 =
      if (data0.size > size) {
        val (_, q) = data0.dequeue
        q
      } else data0

    History(data1, size)
  }
}
case class LoadBalancer[R, S <: ServerState](
    servers: Vector[Server[R, S]],
    history: History,
    taskCost: FiniteDuration = 2.seconds,
    reloadCost: FiniteDuration = 20.seconds,
) {
  private val log = LoggerFactory.getLogger(getClass)

  def done(taskId: TaskId): Option[LoadBalancer[R, S]] = {
    Some(copy(servers = servers.map(_.done(taskId))))
  }

  def addServer(server: Server[R, S]): LoadBalancer[R, S] = {
    copy(servers = server +: servers)
  }

  def removeServer(ref: R): LoadBalancer[R, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Server[R, S] = random(servers)

  def add(task: Task): Option[(Server[R, S], LoadBalancer[R, S])] = {
    log.info("Task added: {}", task.taskId)

    val (availableServers, unavailableServers) =
      servers.partition(_.state.isReady)

    if (availableServers.nonEmpty) {
      val updatedHistory = history.add(task.toRecord)
      val hits = availableServers.filterNot(s => s.currentConfig.needsReload(task.config))
      val overBooked = hits.forall { s =>
        s.cost(taskCost, reloadCost) > reloadCost
      }
      val cacheMiss = hits.isEmpty
      val selectedServer = if (cacheMiss || overBooked) {
        // we try to find a new configuration to minimize the distance with the historical data
        val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]
        randomMin(availableServers) { s =>
          val config = task.config
          val newConfigsHistogram = availableServers.map(olds => if (olds.id == s.id) config else olds.currentConfig).to[Histogram]
          val distance = historyHistogram.distance(newConfigsHistogram)
          val load = s.cost(taskCost, reloadCost)
          (distance, load)
        }
      } else {
        random(hits)
      }
      val updatedServers = availableServers.map(olds => if (olds.id == selectedServer.id) olds.add(task) else olds)
      Some(
        (
          selectedServer,
          copy(
            servers = updatedServers ++ unavailableServers,
            history = updatedHistory
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

  // find min by f, select one min at random
  private def randomMin[A, B: Ordering](xs: Seq[A])(f: A => B): A = {
    val evals = xs.map(x => (x, f(x)))
    val min = evals.minBy(_._2)._2
    val ranking = evals.filter { case (_, e) => e == min }
    ranking(Random.nextInt(ranking.size))._1
  }

  // select one at random
  private def random[T](xs: Vector[T]): T = xs(Random.nextInt(xs.size))
}
