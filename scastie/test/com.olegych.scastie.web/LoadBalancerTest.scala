package com.olegych.scastie
package web

import api._

class LoadBalancerTest extends LoadBalancerTestUtils {
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
          1 * "c3" // last in queue
        )
      )
    assertConfigs(add(balancer, "c8"))(
      2 * "c1",
      1 * "c2",
      1 * "c4",
      1 * "c8"
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

  test("cache hit") {
    pending
  }

  test("reconfigure busy configuration") {
    pending
  }  
}
