package com.olegych.scastie
package balancer

import api._

import scala.collection.immutable.Queue

class LoadBalancerTest extends LoadBalancerTestUtils {
  scala.util.Random.setSeed(0)

  test("simple cache miss") {
    val balancer =
      TestLoadBalancer(
        servers(
          2 * "c1",
          1 * "c2",
          1 * "c3",
          1 * "c4"
        ),
        history(
          10 * "c1",
          5 * "c2",
          1 * "c7",
          1 * "c6",
          1 * "c5",
          1 * "c4",
          1 * "c3"
        )
      )

    assertConfigs(add(balancer, config("c8")))(
      2 * "c1",
      1 * "c2",
      1 * "c4",
      1 * "c8"
    )
  }

  test("cache hit (simple)") {
    val balancer =
      TestLoadBalancer(
        servers(
          5 * "c1"
        ),
        history(
          20 * "c1"
        )
      )

    assertConfigs(add(balancer, config("c1")))(
      5 * "c1"
    )
  }

  test("draw cache miss") {
    val balancer =
      TestLoadBalancer(
        servers(
          5 * "c1"
        ),
        history(
          20 * "c1"
        )
      )
    assertConfigs(add(balancer, config("c2")))(
      4 * "c1",
      1 * "c2"
    )
  }

  test("reconfigure busy configuration") {
    val tasks = (1 to 5)
      .map(
        i => Task(config("c1"), Ip(i.toString), TestTaskId(i))
      )
      .toSeq

    val s1Tasks = Queue(tasks: _*)

    val balancer =
      TestLoadBalancer(
        Vector(
          server("c1", s1Tasks),
          server("c2"),
          server("c3"),
          server("c4"),
          server("c5")
        ),
        history(
          10 * "c1",
          5 * "c2",
          1 * "c7",
          1 * "c6",
          1 * "c5",
          1 * "c4",
          1 * "c3"
        )
      )

    assertConfigs(add(balancer, config("c1")))(
      1 * "c1",
      1 * "c1",
      1 * "c2",
      1 * "c4",
      1 * "c5"
    )
  }

  test("do not reconfigure if some configuration if not busy") {
    val tasks = (1 to 5).map(
      i => Task(config("c1"), Ip(i.toString), TestTaskId(i))
    )
    val s1Tasks = Queue(tasks: _*)

    val balancer =
      TestLoadBalancer(
        Vector(
          server("c1", s1Tasks),
          server("c1"),
          server("c2"),
          server("c3"),
          server("c4")
        ),
        history(
          10 * "c1",
          5 * "c2",
          1 * "c7",
          1 * "c6",
          1 * "c5",
          1 * "c4",
          1 * "c3"
        )
      )

    assertConfigs(add(balancer, config("c1")))(
      1 * "c1",
      1 * "c1",
      1 * "c2",
      1 * "c3",
      1 * "c4"
    )
  }

  test("server notify when it's done") {
    val balancer =
      TestLoadBalancer(
        servers(1 * "c1"),
        history(1 * "c1")
      )

    val server = balancer.servers.head
    assert(server.mailbox.isEmpty)

    val c1 = config("c1")
    val taskId = TestTaskId(1)

    val (assigned, balancer0) = balancer.add(Task(c1, nextIp, taskId)).get

    assert(assigned.ref == server.ref)
    assert(balancer0.servers.head.mailbox.size == 1)

    val balancer1 = balancer0.done(taskId)
    assert(balancer1.servers.head.mailbox.isEmpty)
  }

  test("run two tasks") {
    val balancer =
      TestLoadBalancer(
        servers(1 * "c1"),
        history(1 * "c1")
      )

    val server = balancer.servers.head
    assert(server.mailbox.isEmpty)
    assert(server.currentTaskId.isEmpty)

    val taskId1 = TestTaskId(1)
    val (assigned0, balancer0) =
      balancer.add(Task(config("c1"), nextIp, taskId1)).get

    val server0 = balancer0.servers.head

    assert(assigned0.ref == server.ref)
    assert(server0.mailbox.size == 1)
    assert(server0.currentTaskId.contains(taskId1))

    val taskId2 = TestTaskId(2)
    val (assigned1, balancer1) =
      balancer0.add(Task(config("c2"), nextIp, taskId2)).get

    val server1 = balancer1.servers.head
    assert(server1.mailbox.size == 2)
    assert(server1.currentTaskId.contains(taskId1))

    val balancer2 = balancer1.done(taskId1)
    val server2 = balancer2.servers.head
    assert(server2.mailbox.size == 1)
    assert(server2.currentTaskId.contains(taskId2))

    val balancer3 = balancer2.done(taskId2)
    val server3 = balancer3.servers.head
    assert(server3.mailbox.isEmpty)
    assert(server3.currentTaskId.isEmpty)
  }

  test("remove a server") {
    val ref = TestServerRef(1)

    val balancer = TestLoadBalancer(
      Vector(TestServer(ref, config("c1"))),
      History(Queue(), size = 1)
    )
    assert(balancer.removeServer(ref).servers.isEmpty)
  }

  test("empty balancer") {

    val emptyBalancer = TestLoadBalancer(
      servers = Vector(),
      history = History(Queue(), size = 1)
    )

    val task = Task(config("c1"), nextIp, TestTaskId(1))

    assert(emptyBalancer.add(task).isEmpty)
  }
}
