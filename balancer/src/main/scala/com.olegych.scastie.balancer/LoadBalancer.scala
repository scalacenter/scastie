package com.olegych.scastie
package balancer

import api.{SnippetId, SnippetUserPart}

import utils._

import scala.collection.immutable.Queue
import util.Random

import System.{lineSeparator => nl}

import org.slf4j.LoggerFactory

object Ip {
  implicit val ord = Ordering.by(unapply)
}
case class Ip(v: String)
case class Record[C](config: C, ip: Ip)

object Task {
  implicit def ordSnippetUserPart: Ordering[SnippetUserPart] =
    Ordering.by(SnippetUserPart.unapply)

  implicit def ordSnippetId: Ordering[SnippetId] =
    Ordering.by(SnippetId.unapply)

  implicit def ord[C: Ordering]: Ordering[Task[C]] =
    Ordering.by(Task.unapply[C])
}

case class Task[C](config: C, ip: Ip, snippetId: SnippetId) {
  def toRecord = Record(config, ip)
}

case class Server[C, S](ref: S, lastConfig: C, mailbox: Queue[Task[C]]) {

  def currentSnippetId: Option[SnippetId] = mailbox.headOption.map(_.snippetId)
  def currentConfig: C = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done: Server[C, S] = {
    val (task, mailbox0) = mailbox.dequeue

    assert(Some(task.snippetId) == currentSnippetId)

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

  //([0s, 10s] upper bound Defined in SbtMain)
  val averageRunTime = 3 // s

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
case class LoadBalancer[C: Ordering, S](
    servers: Vector[Server[C, S]],
    history: History[C]
) {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val configs = servers.map(_.currentConfig)

  def done(snippetId: SnippetId): LoadBalancer[C, S] = {
    log.info(s"Task done: $snippetId")
    val res =
      servers.zipWithIndex.find(_._1.currentSnippetId == Some(snippetId))
    if (res.nonEmpty) {
      val (server, i) = res.get
      copy(servers = servers.updated(i, server.done))
    } else {
      val serversSnippetIds =
        servers.flatMap(_.currentSnippetId).mkString("[", ", ", "]")
      log.info(
        s"""cannot find snippetId: $snippetId from servers task ids $serversSnippetIds"""
      )
      this
    }
  }

  def removeServer(ref: S): LoadBalancer[C, S] = {
    copy(servers = servers.filterNot(_.ref == ref))
  }

  def getRandomServer: Server[C, S] = random(servers)

  def add(task: Task[C]): (Server[C, S], LoadBalancer[C, S]) = {
    assert(servers.size > 0, "All instances are down")

    val updatedHistory = history.add(task.toRecord)
    lazy val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]

    val hits = servers.indices
      .to[Vector]
      .filter(i => servers(i).currentConfig == task.config)

    def overBooked =
      hits.forall(i => servers(i).cost > Server.averageReloadTime)
    def cacheMiss = hits.isEmpty

    log.info(s"Balancing config: ${task.config.hashCode}")
    debugState(historyHistogram)

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

    debugMin(i, config, newConfigsHistogram, distance)

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

  private def debugMin(targetServerIndex: Int,
                       config: C,
                       newConfigsHistogram: Histogram[C],
                       distance: Double): Unit = {
    val i = targetServerIndex
    val config = servers(i).currentConfig
    val d2 = Math.floor(distance * 100).toInt

    val load = servers(i).cost
    log.debug(s"== Server($i) load: $load(s) config: $config distance: $d2 ==")
    log.debug(newConfigsHistogram.toString)
  }

  private def debugState(updatedHistory: Histogram[C]): Unit = {
    val configHistogram = servers.map(_.currentConfig).to[Histogram]

    log.debug("== History ==")
    log.debug(updatedHistory.toString)
    log.debug("== Configs ==")
    log.debug(configHistogram.toString)
  }

  def debug: String = {
    val historyConfig = history.data.map(_.config)
    val historyHistogram = historyConfig.map(_.hashCode).to[Histogram]

    val serversConfig = servers.map(_.currentConfig)
    val serversHistogram = serversConfig.map(_.hashCode).to[Histogram]

    val configs =
      (serversConfig ++ historyConfig)
        .map(x => x.hashCode -> x)
        .toMap
        .values
        .map(config => s"""|Config #${config.hashCode}
                           |$config""".stripMargin)
        .mkString(nl)

    // ref: S, lastConfig: C, mailbox: Queue[Task[C]]
    val serversDebug =
      servers
        .map(
          server =>
            s"ref: ${server.ref}, lastConfig: ${server.lastConfig.hashCode}, tasks: ${Multiset(server.mailbox)}"
        )
        .mkString(nl)

    s"""|== Servers ==
        |$serversDebug
        |== History ==
        |$historyHistogram
        |== Servers ==
        |$serversHistogram
        |== Configs ==
        |$configs""".stripMargin
  }
}
