package com.olegych.scastie
package balancer

import api._
import utils._

import scala.collection.immutable.Queue
import util.Random

import org.slf4j.LoggerFactory

case class Ip(v: String)
case class Record[C](config: C, ip: Ip)

case class Task[C](config: C, ip: Ip, taskId: TaskId) {
  def toRecord = Record(config, ip)
}

case class Server[C, S](ref: S, lastConfig: C, mailbox: Queue[Task[C]]) {

  def currentTaskId: Option[TaskId] = mailbox.headOption.map(_.taskId)
  def currentConfig: C = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done: Server[C, S] = {
    val (task, mailbox0) = mailbox.dequeue

    assert(currentTaskId.contains(task.taskId))

    copy(
      lastConfig = task.config,
      mailbox = mailbox0
    )
  }

  def add(task: Task[C]): Server[C, S] = {
    copy(mailbox = mailbox.enqueue(task))
  }

  def cost: Int = {
    import Server._

    val reloadsPenalties =
      mailbox.sliding(2).foldLeft(0) { (acc, slide) =>
        val reloadPenalty =
          slide match {
            case Queue(x, y) =>
              if (x.config != y.config) averageReloadTime
              else 0
            case _ => 0
          }
        acc + reloadPenalty
      }

    reloadsPenalties + mailbox.map(_ => averageRunTime).sum
  }
}

object Server {
  // (found by experimentation)
  val averageReloadTime = 10 //s

  //([0s, 30s] upper bound Defined in SbtMain)
  val averageRunTime = 15 // s

  def apply[C, S](ref: S, config: C): Server[C, S] =
    Server(
      ref = ref,
      lastConfig = config,
      mailbox = Queue()
    )
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
case class LoadBalancer[C, S](
    servers: Vector[Server[C, S]],
    history: History[C]
) {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val configs = servers.map(_.currentConfig)

  def done(taskId: TaskId): LoadBalancer[C, S] = {
    log.info(s"Task done: $taskId")
    val serverRunningTask =
      servers.zipWithIndex.find(_._1.currentTaskId.contains(taskId))

    serverRunningTask match {
      case Some((server, index)) =>
        copy(servers = servers.updated(index, server.done))
      case None =>
        val serversTaskIds =
          servers.flatMap(_.currentTaskId).mkString("[", ", ", "]")
        throw new Exception(
          s"""cannot find taskId: $taskId from servers task ids $serversTaskIds"""
        )
    }
  }

  def removeServer(ref: S): LoadBalancer[C, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Server[C, S] = random(servers)

  def add(task: Task[C]): (Server[C, S], LoadBalancer[C, S]) = {
    log.info("Task added: {}", task.taskId)

    if (servers.size <= 0) {
      val msg = "All instances are down, shutting down the server"
      log.error(msg)
      throw new Exception(msg)
    }

    val updatedHistory = history.add(task.toRecord)
    lazy val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]

    val hits = servers.indices
      .to[Vector]
      .filter(i => servers(i).currentConfig == task.config)

    def overBooked =
      hits.forall(i => servers(i).cost > Server.averageReloadTime)
    def cacheMiss = hits.isEmpty

    val selectedServerIndice =
      if (cacheMiss || overBooked) {
        // we try to find a new configuration to minimize the distance with
        // the historical data
        randomMin(configs.indices) { i =>
          val config = task.config
          val distance = distanceFromHistory(i, config, historyHistogram)
          val load = servers(i).cost
          (distance, load)
        }
      } else {
        random(hits)
      }

    val updatedServers = {
      val i = selectedServerIndice
      servers.updated(i, servers(i).add(task))
    }

    (servers(selectedServerIndice),
     LoadBalancer(updatedServers, updatedHistory))
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
    ranking(util.Random.nextInt(ranking.size))._1
  }

  // select one at random
  private def random[T](xs: Vector[T]): T = xs(Random.nextInt(xs.size))
}
