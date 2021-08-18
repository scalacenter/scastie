package com.olegych.scastie.util

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class ShowConfigTest extends AnyFunSuite with Matchers {
  private val config = ConfigFactory.parseString(
    """
      |a: 1
      |b.c = "str"
      |d: {
      |  d1 = 1s
      |  d2.x = ${a}
      |}
      |""".stripMargin).resolve()

  test("single simple path") {
    ShowConfig(config, "a") mustBe "a: 1"
  }

  test("complex path") {
    ShowConfig(config, "d") mustBe
      """d: {
        |    "d1" : "1s",
        |    "d2" : {
        |        "x" : 1
        |    }
        |}""".stripMargin
  }

  test("with comments and line breaks") {
    ShowConfig(config,
      """
        | # comment
        |b.c
        |
        |""".stripMargin) mustBe
      """
        | # comment
        |b.c: "str"
        |""".stripMargin
  }

  test("with group") {
    ShowConfig(config,
      """|d {
         |    d2.x
         |}
         |""".stripMargin) mustBe
      """|d {
         |    d2.x: 1
         |}""".stripMargin
  }
}
