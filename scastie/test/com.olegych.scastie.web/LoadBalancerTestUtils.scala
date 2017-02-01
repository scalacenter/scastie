package com.olegych.scastie
package web

import org.scalatest.{FunSuite, Assertion}

import scala.collection.immutable.Queue

trait LoadBalancerTestUtils extends FunSuite {
  implicit class IntExtension(n: Int) {
    def *(v: String): Seq[String] = List.fill(n)(v)
  }

  type TestLoadBalancer = LoadBalancer[String, String]

  def add(balancer: TestLoadBalancer, config: String): TestLoadBalancer = {
    val (_, balancer0) = balancer.add(Record(config, nextIp))
    balancer0
  }

  def assertConfigs(balancer: TestLoadBalancer)(columns: Seq[String]*): Assertion = {
    assertMultiset(
      balancer.servers.map(_.tailConfig),
      columns.flatten
    )
  }

  def assertMultiset[T](xs: Seq[T], ys: Seq[T]): Assertion =
    assert(multiset(xs) == multiset(ys))

  case class Multiset[T](inner: Map[T, Int]) {
    override def toString: String =
      inner.toList
        .sortBy(_._2)
        .reverse
        .map {
          case (k, v) =>
            List.fill(v)(k).mkString(", ")
        }
        .mkString("Multiset(", ", ", ")")
  }

  def multiset[T](xs: Seq[T]): Multiset[T] =
    Multiset(xs.groupBy(x => x).map { case (k, vs) => (k, vs.size) })

  var serverId = 0
  def server(config: String) = {
    val t = Server("s" + serverId, config)
    serverId += 1
    t
  }

  def servers(columns: Seq[String]*): Vector[Server[String, String]] = {
    columns.to[Vector].flatten.map(server)
  }

  var currentIp = 0
  def nextIp = {
    val t = Ip("ip" + currentIp)
    currentIp += 1
    t
  }

  def history(columns: Seq[String]*): History[String] = {
    val records =
      columns.to[Vector].flatten.map(config => Record(config, nextIp))
    History(Queue(records: _*))
  }
}
