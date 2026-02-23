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

  test("[ScalaCliLoadBalancer] prefers server with matching directives") {
    val cats = "//> using dep org.typelevel::cats-core:2.10.0\nprintln(1)"
    val zio = "//> using dep dev.zio::zio:2.0.0\nprintln(1)"

    val balancer = ScalaCliLoadBalancer(Vector(
      scalaCliServer(cats),
      scalaCliServer(zio),
    ))

    val balancer1 = addScalaCli(balancer, scalaCliConfig(cats))
    val catsServer = balancer1.servers.find(_.mailbox.nonEmpty).get
    assert(catsServer.lastConfig.code == cats)
  }

  test("[ScalaCliLoadBalancer] prefers server without reload over less busy server") {
    val cats = "//> using dep org.typelevel::cats-core:2.10.0\nprintln(1)"
    val zio = "//> using dep dev.zio::zio:2.0.0\nprintln(1)"

    /* Server 0: matching directives, 1 task in mailbox. Server 1: different directives, empty */
    val matchingServer = scalaCliServer(cats, mailbox = Vector(
      Task(scalaCliConfig(cats), nextIp, TestTaskId(100), Instant.now)
    ))
    val emptyServer = scalaCliServer(zio)

    val balancer = ScalaCliLoadBalancer(Vector(matchingServer, emptyServer))
    val balancer1 = addScalaCli(balancer, scalaCliConfig(cats))

    val assigned = balancer1.servers.find(_.mailbox.size == 2).get
    assert(assigned.id == matchingServer.id)
  }

  test("[ScalaCliLoadBalancer] falls back to least busy when no directive match") {
    val cats = "//> using dep org.typelevel::cats-core:2.10.0\nprintln(1)"
    val zio = "//> using dep dev.zio::zio:2.0.0\nprintln(1)"
    val circe = "//> using dep io.circe::circe-core:0.14.0\nprintln(1)"

    /* Neither server matches the incoming task's directives */
    val busyServer = scalaCliServer(cats, mailbox = Vector(
      Task(scalaCliConfig(cats), nextIp, TestTaskId(100), Instant.now)
    ))
    val idleServer = scalaCliServer(zio)

    val balancer = ScalaCliLoadBalancer(Vector(busyServer, idleServer))
    val balancer1 = addScalaCli(balancer, scalaCliConfig(circe))

    val assigned = balancer1.servers.find(_.mailbox.exists(_.config.code == circe)).get
    assert(assigned.id == idleServer.id)
  }

  test("[ScalaCliLoadBalancer] allows reload when server is busy (mailbox >= 3)") {
    val cats = "//> using dep org.typelevel::cats-core:2.10.0\nprintln(1)"
    val zio = "//> using dep dev.zio::zio:2.0.0\nprintln(1)"

    def makeTask(i: Int) = Task(scalaCliConfig(cats), nextIp, TestTaskId(i), Instant.now)

    /* Server with matching directives but 3 tasks in mailbox */
    val busyServer = scalaCliServer(cats, mailbox = Vector(makeTask(100), makeTask(101), makeTask(102)))
    /* Server with different directives but empty */
    val idleServer = scalaCliServer(zio)

    val balancer = ScalaCliLoadBalancer(Vector(busyServer, idleServer))
    val balancer1 = addScalaCli(balancer, scalaCliConfig(cats))

    val assigned = balancer1.servers.find(_.mailbox.size == 1).get
    assert(assigned.id == idleServer.id)
  }

}
