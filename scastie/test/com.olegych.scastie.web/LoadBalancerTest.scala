package com.olegych.scastie
package web

import scala.collection.immutable.Queue

class LoadBalancerTest extends LoadBalancerTestUtils {
  util.Random.setSeed(0)

  test("simple cache miss") {
    val balancer =
      LoadBalancer(
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

    assertConfigs(add(balancer, "c8"))(
      2 * "c1",
      1 * "c2",
      1 * "c4",
      1 * "c8"
    )
  }

  test("cache hit (simple)") {
    val balancer =
      LoadBalancer(
        servers(
          5 * "c1"
        ),
        history(
          20 * "c1"
        )
      )

    assertConfigs(add(balancer, "c1"))(
      5 * "c1"
    )
  }

  test("draw cache miss") {
    val balancer =
      LoadBalancer(
        servers(
          5 * "c1"
        ),
        history(
          20 * "c1"
        )
      )
    assertConfigs(add(balancer, "c2"))(
      4 * "c1",
      1 * "c2"
    )
  }

  test("reconfigure busy configuration") {
    val tasks = (1 to 5).map(i => Task("c1", Ip(i.toString), i))
    val balancer =
      LoadBalancer(
        Vector(
          Server("s1", "c1", Queue(tasks: _*)),
          Server("s1", "c2", Queue()),
          Server("s1", "c3", Queue()),
          Server("s1", "c4", Queue()),
          Server("s1", "c5", Queue())
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

    assertConfigs(add(balancer, "c1"))(
      1 * "c1",
      1 * "c1",
      1 * "c2",
      1 * "c4",
      1 * "c5"
    )
  }

  test("dont reconfigure if some configuration if not busy") {
    val tasks = (1 to 5).map(i => Task("c1", Ip(i.toString), i))
    val balancer =
      LoadBalancer(
        Vector(
          Server("s1", "c1", Queue(tasks: _*)),
          Server("s1", "c1", Queue()),
          Server("s1", "c2", Queue()),
          Server("s1", "c3", Queue()),
          Server("s1", "c4", Queue())
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

    assertConfigs(add(balancer, "c1"))(
      1 * "c1",
      1 * "c1",
      1 * "c2",
      1 * "c3",
      1 * "c4"
    )
  }

  test("server notify when it's done") {
    val balancer =
      LoadBalancer(
        servers(1 * "c1"),
        history(1 * "c1")
      )

    val server = balancer.servers.head
    assert(server.mailbox.size == 0)
    
    val config = "c1"
    val taskId = 0
    val (assigned, balancer0) = balancer.add(Task(config, nextIp, taskId))

    assert(assigned.ref == server.ref)
    assert(balancer0.servers.head.mailbox.size == 1)

    val balancer1 = balancer0.done(taskId)
    assert(balancer1.servers.head.mailbox.size == 0)
  }

  test("run two tasks") {
    val balancer =
      LoadBalancer(
        servers(1 * "c1"),
        history(1 * "c1")
      )

    val server = balancer.servers.head
    assert(server.mailbox.size == 0)
    assert(server.currentTaskId == None)
    
    val taskId1 = 1
    val (assigned0, balancer0) = balancer.add(Task("c1", nextIp, taskId1))

    val server0 = balancer0.servers.head

    assert(assigned0.ref == server.ref)
    assert(server0.mailbox.size == 1)
    assert(server0.currentTaskId == Some(taskId1))

    val taskId2 = 2
    val (assigned1, balancer1) = balancer0.add(Task("c2", nextIp, taskId2))

    val server1 = balancer1.servers.head
    assert(server1.mailbox.size == 2)
    assert(server1.currentTaskId == Some(taskId1))

    val balancer2 = balancer1.done(taskId1)
    val server2 = balancer2.servers.head
    assert(server2.mailbox.size == 1)
    assert(server2.currentTaskId == Some(taskId2))

    val balancer3 = balancer2.done(taskId2)
    val server3 = balancer3.servers.head
    assert(server3.mailbox.size == 0)
    assert(server3.currentTaskId == None)
  }

  test("remove a server") {
    val ref = "s1"
    val balancer = LoadBalancer(
      Vector(Server("s1", "c1")),
      History(Queue(), size = 1)
    )
    assert(balancer.removeServer(ref).servers.size == 0)
  }
}
