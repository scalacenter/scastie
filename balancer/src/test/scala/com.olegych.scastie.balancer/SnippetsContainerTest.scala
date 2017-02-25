package com.olegych.scastie
package balancer

import api._

import java.nio.file._

import org.scalatest.FunSuite

class SnippetsContainerTest extends FunSuite {
  test("read/write") {
    val container = new SnippetsContainer(Files.createTempDirectory("test"))

    val inputs = Inputs.default

    val id = container.writeSnippet(inputs, None)
    val obtained = container.readSnippet(id).get

    assert(obtained == inputs)
  }
}
