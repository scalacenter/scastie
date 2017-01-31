package com.olegych.scastie
package web

import api._

import org.scalatest.{FunSuite, Assertion}

import java.nio.file._

import scala.util.Random
import scala.collection.immutable.Queue

class LoadBalancerTest extends FunSuite {
  test("cache miss") {
    val balancer =
      LoadBalancing(
        Vector(
          2 * "c1",
          1 * "c2",
          1 * "c3",
          1 * "c4"
        ).flatten.map(server),
        history(
          10 * "c1",
           5 * "c2",
           1 * "c4",
           1 * "c5",
           1 * "c6",
           1 * "c7",
           1 * "c3"
        )
      )

    val (_, balancer0) = balancer.add(Record("c8", nextIp))

    assertMultiset(
      balancer0.servers.map(_.tailConfig),
      List(
        2 * "c1",
        1 * "c2",
        1 * "c4",
        1 * "c8"
      ).flatten
    )
  }

  test("cache hit") {
    pending
  }

  test("reconfigure busy configuration") {
    pending
  }

  implicit class IntExtension(n: Int){
    def *(v: String): Seq[String] = List.fill(n)(v)
  }

  private def assertMultiset[T](xs: Seq[T], ys: Seq[T]): Assertion =
    assert(multiset(xs) == multiset(ys))

  case class Multiset[T](inner: Map[T, Int]) {
    override def toString: String = 
      inner.toList.sortBy(_._2).reverse.map{ case (k, v) =>
        List.fill(v)(k).mkString(", ")
      }.mkString("Multiset(", ", ", ")")
  }

  private def multiset[T](xs: Seq[T]): Multiset[T] =
    Multiset(xs.groupBy(x => x).map{ case (k, vs) => (k, vs.size) })


  private var serverId = 0
  private def server(config: String) = {
    val t = Server("s" + serverId, config)
    serverId += 1
    t
  }

  private var currentIp = 0
  private def nextIp = {
    val t = Ip("ip" + currentIp)
    currentIp += 1
    t
  }

  private def history(columns: Seq[String]*): History[String] = {
    val records = 
      columns.flatten.map(config =>
        Record(config, nextIp)
      )
    History(Queue(records: _*))
  }
}
