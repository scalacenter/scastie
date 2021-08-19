package com.olegych.scastie.sbt

import org.scalatest.Assertions._
import org.scalatest.funsuite.AnyFunSuite
import com.olegych.scastie.sbt.FormatActor
import com.olegych.scastie.api.ScalaTarget

class FormatActorTest extends AnyFunSuite {
  test("format should accept scala 2 code") {
    val code = """
    |class A {
    |val a: Int=3
    |}""".stripMargin
    val output = """class A {
    |  val a: Int = 3
    |}
    |""".stripMargin
    assert(FormatActor.format(code, false, ScalaTarget.Jvm.default) == Right(output))
  }

  test("format should accept scala 2 worksheets") {
    val code = "val x:Int=41+1"
    val output = "val x: Int = 41 + 1\n"

    assert(FormatActor.format(code, true, ScalaTarget.Jvm.default) == Right(output))
  }

  test("format should accept scala 3 code") {
    val code = """
    |class A {
    |val a: Int=3
    |}""".stripMargin
    val output = """class A {
    |  val a: Int = 3
    |}
    |""".stripMargin
    assert(FormatActor.format(code, false, ScalaTarget.Scala3.default) == Right(output))
  }

  test("format should accept scala 3 worksheets") {
    val code = "val x:Int=41+1"
    val output = "val x: Int = 41 + 1\n"

    assert(FormatActor.format(code, true, ScalaTarget.Scala3.default) == Right(output))
  }
}
