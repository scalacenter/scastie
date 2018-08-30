package com.olegych.scastie.balancer

import com.olegych.scastie.api._
import com.olegych.scastie.balancer.utils.Histogram

import scala.util.Random
import scala.collection.immutable.Queue

import scala.concurrent.duration.FiniteDuration

import org.slf4j.LoggerFactory

case class Ip(v: String)
case class Record[C](config: C, ip: Ip)

case class Task[C, T <: TaskId](config: C, ip: Ip, taskId: T) {
  def toRecord: Record[C] = Record(config, ip)
}

case class History[C](data: Queue[Record[C]], size: Int) {
  def add(record: Record[C]): History[C] = {
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
case class LoadBalancer[C, T <: TaskId, R, S <: ServerState](
    servers: Vector[Server[C, T, R, S]],
    history: History[C],
    taskCost: FiniteDuration,
    reloadCost: FiniteDuration,
    needsReload: (C, C) => Boolean
) {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val configs = servers.map(_.currentConfig)

  def done(taskId: T): Option[LoadBalancer[C, T, R, S]] = {
    log.info(s"Task done: $taskId")
    val serverRunningTask =
      servers.zipWithIndex.find(_._1.currentTaskId.contains(taskId))

    serverRunningTask match {
      case Some((server, index)) =>
        Some(copy(servers = servers.updated(index, server.done)))
      case None =>
        None
    }
  }

  def addServer(server: Server[C, T, R, S]): LoadBalancer[C, T, R, S] = {
    copy(servers = server +: servers)
  }

  def removeServer(ref: R): LoadBalancer[C, T, R, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Server[C, T, R, S] = random(servers)

  def add(
      task: Task[C, T]
  ): Option[(Server[C, T, R, S], LoadBalancer[C, T, R, S])] = {
    log.info("Task added: {}", task.taskId)

    val (availableServers, unavailableServers) =
      servers.partition(_.state.isReady)

    if (availableServers.size > 0) {
      val updatedHistory = history.add(task.toRecord)
      lazy val historyHistogram =
        updatedHistory.data.map(_.config).to[Histogram]

      val hits = availableServers.indices
        .to[Vector]
        .filter(i => availableServers(i).currentConfig == task.config)

      def overBooked =
        hits.forall(
          i =>
            availableServers(i)
              .cost(taskCost, reloadCost, needsReload) > reloadCost
        )

      def cacheMiss = hits.isEmpty

      val selectedServerIndice =
        if (cacheMiss || overBooked) {
          // we try to find a new configuration to minimize the distance with
          // the historical data
          randomMin(configs.indices) { i =>
            val config = task.config
            val distance = distanceFromHistory(i, config, historyHistogram)
            val load =
              availableServers(i).cost(taskCost, reloadCost, needsReload)
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
      if (servers.size == 0) {
        val msg = "All instances are down"
        log.error(msg)
      }

      None
    }
  }

  private def distanceFromHistory(targetServerIndex: Int,
                                  config: C,
                                  historyHistogram: Histogram[C]): Double = {
    val i = targetServerIndex
    val newConfigs = configs.updated(i, config)
    val newConfigsHistogram = newConfigs.to[Histogram]
    val distance = historyHistogram.distance(newConfigsHistogram)

    distance
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
