package com.olegych.scastie
package web

import scala.collection.immutable.Queue
import util.Random

import System.{lineSeparator => nl}

import org.slf4j.LoggerFactory

case class MultiMap[K, V](vs: Map[K, List[V]])
case class Server[C, S](ref: S, tailConfig: C, mailbox: Queue[C]) {
  def done(config: C): Server[C, S] = {
    val (topConfig, mailbox0) = mailbox.dequeue
    assert(config == topConfig)
    copy(mailbox = mailbox0)
  }
  def add(config: C): Server[C, S] = copy(mailbox = mailbox.enqueue(config), tailConfig = config)
  def cost: Int = {
    import Server._  

    val reloadsPenalties =
      mailbox.sliding(2).foldLeft(0){(acc, slide) =>
        val reloadPenalty =
          slide match {
            case Queue(x, y) =>
              if(x != y) averageReloadTime
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
  val averageRunTime = 3 // s }

  def apply[C, S](ref: S, config: C): Server[C, S] = 
    Server(ref, config, Queue())
}
case class Ip(v: String)
case class Record[C](config: C, ip: Ip)
case class History[C](data: Queue[Record[C]]){
  def add(record: Record[C]): History[C] = {
    // the user has changed configuration, we assume he will not go back to the
    // previous configuration
    val (_, data0) =
      data
        .filterNot(_.ip == record.ip)
        .enqueue(record)
        .dequeue

    History(data0)
  }
}
case class LoadBalancer[C: Ordering, S](
  servers: Vector[Server[C, S]],
  history: History[C]
) {
  private val log = LoggerFactory.getLogger(getClass)

  private lazy val configs = servers.map(_.tailConfig)

  def done(ref: S, config: C): LoadBalancer[C, S] = {
    log.info(s"Task done: ${config.hashCode}")
    val res = servers.zipWithIndex.find(_._1.ref == ref)
    assert(res.nonEmpty, {
      val refs = servers.map(_.ref).mkString("[", ", ", "]")
      s"""cannot find server ref: $ref from $refs"""
    })
    val (server, i) = res.get
    copy(servers = servers.updated(i, server.done(config)))
  }
  def add(record: Record[C]): (Server[C, S], LoadBalancer[C, S]) = {
    val updatedHistory = history.add(record)
    lazy val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]

    val hits = servers.indices.to[Vector].filter(i =>
      servers(i).tailConfig == record.config
    )

    def overBooked = hits.forall(i => servers(i).cost > Server.averageReloadTime)
    def cacheMiss = hits.isEmpty

    log.info(s"Balancing config: ${record.config.hashCode}")
    debugState(historyHistogram)

    val selectedServerIndice = 
      if(cacheMiss || overBooked) {
        // we try to find a new configuration to minimize the distance with
        // the historical data
        randomMin(configs.indices){i =>
          val config = record.config
          val distance = distanceFromHistory(i, config, historyHistogram)
          val load = servers(i).cost
          (distance, load)
        }
      } else {
        random(hits)
      }

    val updatedServers = {
      val i = selectedServerIndice
      servers.updated(i, servers(i).add(record.config))
    }

    (servers(selectedServerIndice), LoadBalancer(updatedServers, updatedHistory))
  }

  private def distanceFromHistory(targetServerIndex: Int, config: C, 
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
    val min = f(xs.minBy(f))
    val ranking = xs.filter(x => f(x) == min)
    ranking(util.Random.nextInt(ranking.size))
  }

  // select one at random
  private def random[T](xs: Vector[T]): T = xs(Random.nextInt(xs.size))

  private def debugMin(targetServerIndex: Int, config: C,
    newConfigsHistogram: Histogram[C], distance: Double): Unit = {
    val i = targetServerIndex
    val config = servers(i).tailConfig
    val d2 = Math.floor(distance * 100).toInt

    val load = servers(i).cost
    log.debug(s"== Server($i) load: $load(s) config: $config distance: $d2 ==")
    log.debug(newConfigsHistogram.toString)
  }

  private def debugState(updatedHistory: Histogram[C]): Unit = {
    val configHistogram = servers.map(_.tailConfig).to[Histogram]

    log.debug("== History ==")
    log.debug(updatedHistory.toString)
    log.debug("== Configs ==")
    log.debug(configHistogram.toString)
  }
}
