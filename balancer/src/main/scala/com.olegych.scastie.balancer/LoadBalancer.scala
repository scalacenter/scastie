package com.olegych.scastie.balancer

import com.olegych.scastie.api._
import com.olegych.scastie.balancer.utils.Histogram

import scala.util.Random
import scala.collection.immutable.Queue
import scala.concurrent.duration.{DurationLong, FiniteDuration}
import org.slf4j.LoggerFactory

case class Ip(v: String)
case class Record(config: Inputs, ip: Ip)

case class Task[T <: TaskId](config: Inputs, ip: Ip, taskId: T) {
  def toRecord: Record = Record(config, ip)
}

case class History(data: Queue[Record], size: Int) {
  def add(record: Record): History = {
    // the user has changed configuration, we assume he will not go back to the
    // previous configuration

    val data0 = data.filterNot(_.ip == record.ip).enqueue(record)

    val data1 =
      if (data0.size > size) {
        val (_, q) = data0.dequeue
        q
      } else data0

    History(data1, size)
  }
}
case class LoadBalancer[T <: TaskId, R, S <: ServerState](
    servers: Vector[Server[T, R, S]],
    history: History,
    taskCost: FiniteDuration = 2.seconds,
    reloadCost: FiniteDuration = 20.seconds,
) {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val configs = servers.map(_.currentConfig)

  def done(taskId: T): Option[LoadBalancer[T, R, S]] = {
    Some(copy(servers = servers.map(_.done(taskId))))
  }

  def addServer(server: Server[T, R, S]): LoadBalancer[T, R, S] = {
    copy(servers = server +: servers)
  }

  def removeServer(ref: R): LoadBalancer[T, R, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Server[T, R, S] = random(servers)

  def add(task: Task[T]): Option[(Server[T, R, S], LoadBalancer[T, R, S])] = {
    log.info("Task added: {}", task.taskId)

    val (availableServers, unavailableServers) =
      servers.partition(_.state.isReady)

    if (availableServers.nonEmpty) {
      val updatedHistory = history.add(task.toRecord)
      lazy val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]

      val hits = availableServers.indices
        .to[Vector]
        .filterNot(i => availableServers(i).currentConfig.needsReload(task.config))

      val overBooked = hits.forall { i =>
        availableServers(i).cost(taskCost, reloadCost) > reloadCost
      }

      val cacheMiss = hits.isEmpty

      val selectedServerIndice =
        if (cacheMiss || overBooked) {
          // we try to find a new configuration to minimize the distance with
          // the historical data
          randomMin(configs.indices) { i =>
            val config = task.config
            val distance = distanceFromHistory(i, config, historyHistogram)
            val load = availableServers(i).cost(taskCost, reloadCost)
            (distance, load)
          }
        } else {
          random(hits)
        }

      val updatedServers = {
        val i = selectedServerIndice
        availableServers.updated(i, availableServers(i).add(task))
      }

      Some(
        (
          availableServers(selectedServerIndice),
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

  private def distanceFromHistory(targetServerIndex: Int, config: Inputs, historyHistogram: Histogram[Inputs]): Double = {
    val newConfigsHistogram = configs.updated(targetServerIndex, config).to[Histogram]
    historyHistogram.distance(newConfigsHistogram)
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
