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
    val balancer =
      LoadBalancer(
        Vector(
          Server("s1", "c1", Queue("c1", "c1", "c1", "c1")),
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
    val balancer =
      LoadBalancer(
        Vector(
          Server("s1", "c1", Queue("c1", "c1", "c1", "c1")),
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
    val (assigned, balancer0) = balancer.add(Record(config, nextIp))

    assert(assigned.ref == server.ref)
    assert(balancer0.servers.head.mailbox.size == 1)

    val balancer1 = balancer0.done(server.ref, config)
    assert(balancer1.servers.head.mailbox.size == 0)
  }
}
