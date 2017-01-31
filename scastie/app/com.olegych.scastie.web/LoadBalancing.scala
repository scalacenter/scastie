package com.olegych.scastie
package web

import scala.collection.immutable.Queue

case class MultiMap[K, V](vs: Map[K, List[V]])
case class Server[C, S](ref: S, mailbox: Queue[C], tailConfig: C) {
  def add(config: C) = copy(mailbox = mailbox.enqueue(config), tailConfig = config)
  def cost: Int = {
    // found those values by experimentation
    val averageReloadTime = 10 // s
    val averageRunTime = 3

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
  def apply[C, S](ref: S, config: C): Server[C, S] = 
    Server(ref, Queue(config), config)
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
case class LoadBalancing[C, S](
  servers: Vector[Server[C, S]],
  history: History[C]
) {

  def done(ref: S): LoadBalancing[C, S] = ???
  def add(record: Record[C]): (Server[C, S], LoadBalancing[C, S]) = {
    val updatedHistory = history.add(record)

    def cacheMiss = !servers.exists(_.tailConfig == record.config)
    def overBooked = false // TODO

    val selectedServerIndice = 
      if(cacheMiss || overBooked) {
        val historyHistogram = histogram(updatedHistory.data.map(_.config).toList)

        // we try to find a new configuration to minimize the distance with
        // the historical data
        val configs = servers.map(_.tailConfig)
        val newConfigsRanking =
          configs.indices.map{i =>
            val serverLoad = servers(i).cost
            val newConfigs = configs.updated(i, record.config)
            val newConfigsHistogram = histogram(newConfigs.toList)

            (i, distance(historyHistogram, newConfigsHistogram), serverLoad)
          }

        val (_, dmin, lmin) = newConfigsRanking.minBy{ 
          case (index, distance, load) => (distance, load) 
        }

        val tops = newConfigsRanking.filter{ 
          case (_, d, l) => d == dmin && l == lmin
        }
        val (index, _, _) = tops(util.Random.nextInt(tops.size))
        index
      } else {
        // cache hit
        // overwork ?
        ???
      }


    val updatedServers = {
      val i = selectedServerIndice
      servers.updated(i, servers(i).add(record.config))
    }

    (servers(selectedServerIndice), LoadBalancing(updatedServers, updatedHistory))
  }

  private type Histogram[T] = Map[T, Double]

  private def histogram[T](xs: List[T]): Histogram[T] = {
    xs.groupBy(x => x).mapValues(v => (v.size.toDouble / xs.size.toDouble))
  }

  private def distance[T](h1: Histogram[T], h2: Histogram[T]): Double = {
    innerJoin(h1, h2)((x, y) => (x - y) * (x - y)).values.sum
  }

  private def innerJoin[K, X, Y, Z](m1: Map[K, X], m2: Map[K, Y])(
      f: (X, Y) => Z): Map[K, Z] = {
    m1.flatMap {
      case (k, a) =>
        m2.get(k).map(b => Map(k -> f(a, b))).getOrElse(Map.empty[K, Z])
    }
  }
}
