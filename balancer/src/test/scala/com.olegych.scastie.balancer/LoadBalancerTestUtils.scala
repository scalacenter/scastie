package com.olegych.scastie.balancer

import com.olegych.scastie.api.SbtRunTaskId
import com.olegych.scastie.proto.{SnippetId, Base64UUID}

import com.olegych.scastie.balancer.utils._

import org.scalatest.{FunSuite, Assertion}

import scala.collection.immutable.Queue

trait LoadBalancerTestUtils extends FunSuite with TestUtils {
  type TestLoadBalancer = LoadBalancer[String, String]

  private var taskId = 1000
  def add(balancer: TestLoadBalancer, config: String): TestLoadBalancer = {
    val (_, balancer0) =
      balancer.add(
        Task(config,
             nextIp,
             SbtRunTaskId(SnippetId(Base64UUID(taskId.toString), None)))
      )
    taskId += 1
    balancer0
  }

  def assertConfigs(
      balancer: TestLoadBalancer
  )(columns: Seq[String]*): Assertion = {
    assertMultiset(
      balancer.servers.map(_.currentConfig),
      columns.flatten
    )
  }

  def assertMultiset[T: Ordering](xs: Seq[T], ys: Seq[T]): Assertion =
    assert(Multiset(xs) == Multiset(ys))

  private var serverId = 0
  def server(config: String): Server[String, String] = {
    val t = Server("s" + serverId, config)
    serverId += 1
    t
  }

  def servers(columns: Seq[String]*): Vector[Server[String, String]] = {
    columns.to[Vector].flatten.map(server)
  }

  private var currentIp = 0
  def nextIp: Ip = {
    val t = Ip("ip" + currentIp)
    currentIp += 1
    t
  }

  def history(columns: Seq[String]*): History[String] = {
    val records =
      columns.to[Vector].flatten.map(config => Record(config, nextIp)).reverse

    History(Queue(records: _*), size = 20)
  }
}
