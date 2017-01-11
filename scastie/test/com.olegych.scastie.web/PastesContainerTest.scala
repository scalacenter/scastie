package com.olegych.scastie
package web

import api._

import java.nio.file._

import org.scalatest.FunSuite

class PastesContainerTest extends FunSuite {
  test("read/write") {
    val container = new PastesContainer(Files.createTempDirectory("test"))

    val inputs = Inputs.default

    val id       = container.writePaste(inputs)
    val obtained = container.readPaste(id).get

    assert(obtained == inputs)
  }
}
