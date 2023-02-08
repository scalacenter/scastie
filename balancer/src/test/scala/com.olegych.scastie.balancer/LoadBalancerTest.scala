package com.olegych.scastie
package balancer

import java.time.Instant

class LoadBalancerTest extends LoadBalancerTestUtils {
  test("simple cache miss") {
    val balancer = LoadBalancer(
      servers(
        2 * "c1",
        1 * "c2",
        1 * "c3",
        1 * "c4"
      ),
    )

    assertConfigs(add(balancer, sbtConfig("c8")))(
      1 * "c1",
      1 * "c2",
      1 * "c3",
      1 * "c4",
      1 * "c8"
    )
  }

  test("cache hit (simple)") {
    val balancer = LoadBalancer(
      servers(
        5 * "c1"
      ),
    )

    assertConfigs(add(balancer, sbtConfig("c1")))(
      5 * "c1"
    )
  }

  test("draw cache miss") {
    val balancer = LoadBalancer(
      servers(
        5 * "c1"
      ),
    )
    assertConfigs(add(balancer, sbtConfig("c2")))(
      4 * "c1",
      1 * "c2"
    )
  }

  test("server notify when it's done") {
    val balancer = LoadBalancer(
      servers(1 * "c1"),
    )

    val server = balancer.servers.head
    assert(server.mailbox.isEmpty)

    val c1 = sbtConfig("c1")
    val taskId = TestTaskId(1)

    val (assigned, balancer0) = balancer.add(Task(c1, nextIp, taskId, Instant.now)).get

    assert(assigned.ref == server.ref)
    assert(balancer0.servers.head.mailbox.size == 1)

    val balancer1 = balancer0.done(taskId).get
    assert(balancer1.servers.head.mailbox == Vector())
  }

  test("run two tasks") {
    val balancer = LoadBalancer(
      servers(1 * "c1"),
    )

    val server = balancer.servers.head
    assert(server.mailbox.isEmpty)
    assert(server.currentTaskId.isEmpty)

    val taskId1 = TestTaskId(1)
    val (assigned0, balancer0) =
      balancer.add(Task(sbtConfig("c1"), nextIp, taskId1, Instant.now)).get

    val server0 = balancer0.servers.head

    assert(assigned0.ref == server.ref)
    assert(server0.mailbox.size == 1)
    assert(server0.currentTaskId.contains(taskId1))

    val taskId2 = TestTaskId(2)
    val (assigned1, balancer1) =
      balancer0.add(Task(sbtConfig("c2"), nextIp, taskId2, Instant.now)).get

    val server1 = balancer1.servers.head
    assert(server1.mailbox.size == 2)
    assert(server1.currentTaskId.contains(taskId1))

    val balancer2 = balancer1.done(taskId1).get
    val server2 = balancer2.servers.head
    assert(server2.mailbox.size == 1)
    assert(server2.currentTaskId.contains(taskId2))

    val balancer3 = balancer2.done(taskId2).get
    val server3 = balancer3.servers.head
    assert(server3.mailbox.isEmpty)
    assert(server3.currentTaskId.isEmpty)
  }

  test("remove a server") {
    val ref = TestServerRef(1)

    val balancer = LoadBalancer(
      Vector(Server(ref, sbtConfig("c1"), TestState("default-state"))),
    )
    assert(balancer.removeServer(ref).servers.isEmpty)
  }

  test("empty balancer") {

    val emptyBalancer = LoadBalancer(
      servers = Vector(),
    )

    val task = Task(code("c1"), nextIp, TestTaskId(1), Instant.now)

    assert(emptyBalancer.add(task).isEmpty)
  }

}
