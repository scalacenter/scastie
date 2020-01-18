package com.olegych.scastie.balancer

import java.time.Instant

import com.olegych.scastie.api._
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite

object TestTaskId {
  def apply(i: Int) = TaskId(SnippetId(i.toString, None))
}

case class TestServerRef(id: Int)
case class TestState(state: String, ready: Boolean = true) extends ServerState {
  def isReady: Boolean = ready
}

trait LoadBalancerTestUtils extends AnyFunSuite with TestUtils {
  type TestServer0 = Server[TestServerRef, TestState]

  type TestLoadBalancer0 = LoadBalancer[TestServerRef, TestState]

  @transient private var taskId = 1000
  def add(balancer: TestLoadBalancer0, config: Inputs): TestLoadBalancer0 = synchronized {
    val (_, balancer0) = balancer.add(Task(config, nextIp, TestTaskId(taskId), Instant.now)).get
    taskId += 1
    balancer0
  }

  // Ordering only for debug purposes
  object Multiset {
    def apply[T: Ordering](xs: Seq[T]): Multiset[T] =
      Multiset(xs.groupBy(x => x).map { case (k, vs) => (k, vs.size) })
  }
  case class Multiset[T: Ordering](inner: Map[T, Int]) {
    override def toString: String = {
      val size = inner.values.sum

      inner.toList
        .sortBy { case (k, v) => (-v, k) }
        .map {
          case (k, v) => s"$k($v)"
        }
        .mkString("Multiset(", ", ", s") {$size}")
    }
  }

  def assertConfigs(balancer: TestLoadBalancer0)(columns: Seq[String]*): Assertion = {
    assert(
      Multiset(balancer.servers.map(_.currentConfig.sbtConfigExtra)) == Multiset(
        columns.flatten.map(i => sbtConfig(i.toString).sbtConfigExtra)
      )
    )
  }

  @transient private var serverId = 0
  def server(
      c: String,
      mailbox: Vector[Task] = Vector(),
      state: TestState = TestState("default-state")
  ): TestServer0 = synchronized {
    val t = Server(TestServerRef(serverId), sbtConfig(c), state, mailbox)
    serverId += 1
    t
  }

  def servers(columns: Seq[String]*): Vector[TestServer0] = {
    columns.to(Vector).flatten.map(c => server(c))
  }

  @transient private var currentIp = 0
  def nextIp: Ip = synchronized {
    val t = Ip("ip" + currentIp)
    currentIp += 1
    t
  }

  def server(v: Int): TestServerRef = TestServerRef(v)

  def code(code: String) = Inputs.default.copy(code = code)
  def sbtConfig(sbtConfig: String) = Inputs.default.copy(sbtConfigExtra = sbtConfig)

  def history(columns: Seq[String]*): TaskHistory = {
    val records =
      columns.to(Vector).flatten.map(i => Task(Inputs.default.copy(code = i.toString), nextIp, TestTaskId(1), Instant.now)).reverse

    TaskHistory(Vector(records: _*), maxSize = 20)
  }
}
