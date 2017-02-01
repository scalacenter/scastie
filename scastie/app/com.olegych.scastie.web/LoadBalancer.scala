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
case class History[C: Ordering](data: Queue[Record[C]]){
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
  case class Multiset[T: Ordering](inner: Map[T, Int]) {
    override def toString: String = {
      val size = inner.values.sum

      inner.toList
        .sortBy{ case (k, v) => (v, k)}
        .reverse
        .map { case (k, v) => s"$k($v)"}
        .mkString("Multiset(", ", ", s") {$size}")
    }
  }

  def multiset[T: Ordering](xs: Seq[T]): Multiset[T] =
    Multiset(xs.groupBy(x => x).map { case (k, vs) => (k, vs.size) })

  override def toString: String =
    multiset(data.map(_.config)).toString
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

    val selectedServerIndice = 
      if(cacheMiss || overBooked) {
        val historyHistogram = histogram(updatedHistory.data.map(_.config))
        val configs = servers.map(_.tailConfig)

        println("History")
        println(show(historyHistogram))
        println("Configs")
        println(show(histogram(configs)))

        // we try to find a new configuration to minimize the distance with
        // the historical data
        val newConfigsRanking =
          configs.indices.map{i =>
            val load = servers(i).cost
            val newConfigs = configs.updated(i, record.config)
            val newConfigsHistogram = histogram(newConfigs)

            val d = distance(historyHistogram, newConfigsHistogram)

            def debug(): Unit = {
              val config = servers(i).tailConfig
              val d2 = Math.floor(d * 100).toInt
              println(s"== Server($i) load: $load config: $config distance: $d2 ==")
              println(show(newConfigsHistogram))
            }
            debug()

            (i, d, load)
          }

        // newConfigsRanking.foreach{ case (i, d, l) =>
        //   println("s" + i + " " + servers(i).tailConfig + " " + Math.floor(d * 100).toInt + " " + l)
        // }

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

  private type Histogram[T] = Map[T, Double]

  private def show[T: Ordering](h: Histogram[T]): String = {
    h.toList.sortBy(_._2).reverse.map{ 
      case (k, v) => 
        val pp = Math.floor(100 * v).toInt
        s"$k ${"*" * pp}"

    }.mkString(nl)
  }

  private def histogram[T](xs: Seq[T]): Histogram[T] = {
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
