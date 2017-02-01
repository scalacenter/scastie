package com.olegych.scastie
package web

import scala.collection.immutable.Queue
import util.Random

import System.{lineSeparator => nl}

case class MultiMap[K, V](vs: Map[K, List[V]])
case class Server[C, S](ref: S, mailbox: Queue[C], tailConfig: C) {
  def add(config: C) = copy(mailbox = mailbox.enqueue(config), tailConfig = config)
  def cost: Int = {
    // (found by experimentation)
    val averageReloadTime = 10 //s 

    //([0s, 10s] upper bound Defined in SbtMain)
    val averageRunTime = 3 // s

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
case class LoadBalancer[C: Ordering, S](
  servers: Vector[Server[C, S]],
  history: History[C]
) {

  def done(ref: S): LoadBalancer[C, S] = ???
  def add(record: Record[C]): (Server[C, S], LoadBalancer[C, S]) = {
    val updatedHistory = history.add(record)

    val hits = servers.indices.to[Vector].filter(i =>
      servers(i).tailConfig == record.config
    )

    def overBooked = false // TODO
    def cacheMiss = hits.isEmpty

    // println(s"Adding ${record.config}")

    val selectedServerIndice = 
      if(cacheMiss || overBooked) {
        val historyHistogram = updatedHistory.data.map(_.config).to[Histogram]
        val configs = servers.map(_.tailConfig)

        // println("History")
        // println()
        // println(historyHistogram)
        // println()
        // println("Configs")
        // println()
        // println(configs.to[Histogram])
        // println()

        // we try to find a new configuration to minimize the distance with
        // the historical data
        val newConfigsRanking =
          configs.indices.map{i =>
            val load = servers(i).cost
            val newConfigs = configs.updated(i, record.config)
            val newConfigsHistogram = newConfigs.to[Histogram]

            val distance = historyHistogram.distance(newConfigsHistogram)

            // def debug(): Unit = {
            //   val config = servers(i).tailConfig
            //   val d2 = Math.floor(distance * 100).toInt
            //   println(s"== Server($i) load: $load(s) config: $config distance: $d2 ==")
            //   println()
            //   println(newConfigsHistogram)
            //   println()
            // }
            // debug()

            (i, distance, load)
          }

        val (_, dmin, lmin) = newConfigsRanking.minBy{ 
          case (index, distance, load) => (distance, load) 
        }

        val tops = newConfigsRanking.filter{ 
          case (_, d, l) => d == dmin && l == lmin
        }
        val (index, _, _) = tops(Random.nextInt(tops.size))
        index
      } else {
        random(hits)
      }


    val updatedServers = {
      val i = selectedServerIndice
      servers.updated(i, servers(i).add(record.config))
    }

    (servers(selectedServerIndice), LoadBalancer(updatedServers, updatedHistory))
  }

  // private def randomMinBy[T, R : Ordering](xs: Vector[T])(f: T => R): Int = {
  //   val res = xs.indices.map(i => f)
  // }

  private def random[T](xs: Vector[T]): T = xs(Random.nextInt(xs.size))
}
