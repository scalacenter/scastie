package org.scastie
package balancer

import java.time.Instant
import org.scastie.api.ServerState

class LoadBalancerTest extends LoadBalancerTestUtils {
  test("[SbtLoadBalancer] simple cache miss") {
    val balancer = SbtLoadBalancer(
      sbtServers(
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

  test("[SbtLoadBalancer] cache hit (simple)") {
    val balancer = SbtLoadBalancer(
      sbtServers(
        5 * "c1"
      ),
    )

    assertConfigs(add(balancer, sbtConfig("c1")))(
      5 * "c1"
    )
  }

  test("[SbtLoadBalancer] draw cache miss") {
    val balancer = SbtLoadBalancer(
      sbtServers(
        5 * "c1"
      ),
    )
    assertConfigs(add(balancer, sbtConfig("c2")))(
      4 * "c1",
      1 * "c2"
    )
  }

  test("[SbtLoadBalancer] server notify when it's done") {
    val balancer = SbtLoadBalancer(
      sbtServers(1 * "c1"),
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

  test("[SbtLoadBalancer] run two tasks") {
    val balancer = SbtLoadBalancer(
      sbtServers(1 * "c1"),
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

  test("[SbtLoadBalancer] remove a server") {
    val ref = TestServerRef(1)

    val balancer = SbtLoadBalancer(
      Vector(Server(ref, sbtConfig("c1"), ServerState.Unknown)),
    )
    assert(balancer.removeServer(ref).servers.isEmpty)
  }

  test("[SbtLoadBalancer] empty balancer") {

    val emptyBalancer = SbtLoadBalancer(
      servers = Vector(),
    )

    val task = Task(code("c1"), nextIp, TestTaskId(1), Instant.now)

    assert(emptyBalancer.add(task).isEmpty)
  }

  test("[ScalaCliLoadBalancer] selects server with shortest mailbox") {
    val balancer = ScalaCliLoadBalancer(
      scalaCliServers(
        2 * "c1",
        1 * "c2",
        1 * "c3"
      ),
    )

    val balancer1 = addScalaCli(balancer, scalaCliConfig("code1"))
    assert(balancer1.servers.count(_.mailbox.nonEmpty) == 1)
  }

  test("[ScalaCliLoadBalancer] distributes load") {
    val balancer = ScalaCliLoadBalancer(
      scalaCliServers(
        3 * "c1"
      ),
    )

    val balancer1 = addScalaCli(balancer, scalaCliConfig("code1"))
    val balancer2 = addScalaCli(balancer1, scalaCliConfig("code2"))
    val balancer3 = addScalaCli(balancer2, scalaCliConfig("code3"))

    assert(balancer3.servers.forall(_.mailbox.size == 1))
  }

  test("[ScalaCliLoadBalancer] remove a server") {
    val ref = TestServerRef(1)

    val balancer = ScalaCliLoadBalancer(
      Vector(Server(ref, scalaCliConfig("c1"), ServerState.Unknown)),
    )
    assert(balancer.removeServer(ref).servers.isEmpty)
  }

  test("[ScalaCliLoadBalancer] empty balancer") {
    val emptyBalancer = ScalaCliLoadBalancer(
      servers = Vector(),
    )

    val task = Task(scalaCliConfig("c1"), nextIp, TestTaskId(1), Instant.now)

    assert(emptyBalancer.add(task).isEmpty)
  }

}
