package com.olegych.scastie.balancer

import com.olegych.scastie.api._
import com.olegych.scastie.balancer.utils._

import org.scalatest.{FunSuite, Assertion}

import scala.collection.immutable.Queue

import scala.concurrent.duration._

case class TestTaskId(id: Int) extends TaskId {
  def cost: Int = 1
}

case class TestServerRef(id: Int)
case class TestConfig(config: String)
case class TestState(state: String, ready: Boolean = true) extends ServerState {
  def isReady: Boolean = ready
}

object TestConfig {
  implicit val ordering: Ordering[TestConfig] = Ordering.by { tc: TestConfig =>
    tc.config
  }
}

trait LoadBalancerTestUtils extends FunSuite with TestUtils {
  type TestServer0 = Server[TestConfig, TestTaskId, TestServerRef, TestState]

  object TestLoadBalancer {
    def apply(
        servers: Vector[TestServer0],
        history: History[TestConfig]
    ): TestLoadBalancer0 = {
      LoadBalancer(
        servers = servers,
        history = history,
        taskCost = 1.second,
        reloadCost = 2.seconds,
        needsReload = (a, b) => a == b
      )
    }
  }
  type TestLoadBalancer0 =
    LoadBalancer[TestConfig, TestTaskId, TestServerRef, TestState]

  object TestServer {
    def apply(
        ref: TestServerRef,
        lastConfig: TestConfig,
        mailbox: Queue[Task[TestConfig, TestTaskId]] = Queue(),
        state: TestState = TestState("default-state")
    ): TestServer0 = {
      Server(ref, lastConfig, mailbox, state)
    }
  }

  private var taskId = 1000
  def add(balancer: TestLoadBalancer0, config: TestConfig): TestLoadBalancer0 = {
    val (_, balancer0) =
      balancer
        .add(
          Task(config, nextIp, TestTaskId(taskId))
        )
        .get
    taskId += 1
    balancer0
  }

  def assertConfigs(
      balancer: TestLoadBalancer0
  )(columns: Seq[String]*): Assertion = {
    assertMultiset(
      balancer.servers.map(_.currentConfig),
      columns.flatten.map(i => TestConfig(i))
    )
  }

  def assertMultiset[T: Ordering](xs: Seq[T], ys: Seq[T]): Assertion =
    assert(Multiset(xs) == Multiset(ys))

  private var serverId = 0
  def server(
      c: String,
      mailbox: Queue[Task[TestConfig, TestTaskId]] = Queue(),
      state: TestState = TestState("default-state")
  ): TestServer0 = {
    val t = Server(TestServerRef(serverId), config(c), mailbox, state)
    serverId += 1
    t
  }

  def servers(columns: Seq[String]*): Vector[TestServer0] = {
    columns.to[Vector].flatten.map(c => server(c))
  }

  private var currentIp = 0
  def nextIp: Ip = {
    val t = Ip("ip" + currentIp)
    currentIp += 1
    t
  }

  def server(v: Int): TestServerRef = TestServerRef(v)

  def config(v: String): TestConfig = TestConfig(v)

  def history(columns: Seq[String]*): History[TestConfig] = {
    val records =
      columns.to[Vector].flatten.map(i => Record(TestConfig(i), nextIp)).reverse

    History(Queue(records: _*), size = 20)
  }
}
