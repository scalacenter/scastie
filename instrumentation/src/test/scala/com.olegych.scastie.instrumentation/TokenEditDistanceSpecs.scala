package com.olegych.scastie
package instrumentation

import org.scalatest.funsuite.AnyFunSuite
import scala.meta._

class TokenEditDistanceSpecs extends AnyFunSuite {

  def mkInput(code: String): Input = Input.String(code)

  test("identity mapping for identical input") {
    val code = "val x = 42\nval y = x + 1"
    val input = mkInput(code)
    val Some(edit) = TokenEditDistance(input, input)

    assert(edit.toOriginal(0).exists(_.start == 0))
    assert(edit.toOriginal(10).exists(_.start == 10))
    assert(edit.toOriginalLine(1) == 1)
    assert(edit.toOriginalLine(2) == 2)
  }

  test("mapping after inserting a line") {
    val original = "val x = 42\nval y = x + 1"
    val revised  = "val x = 42\nval z = 100\nval y = x + 1"
    val Some(edit) = TokenEditDistance(mkInput(original), mkInput(revised))

    assert(edit.toOriginalLine(3) == 2)

    val yOffsetRevised = revised.indexOf("y =")
    val yOffsetOriginal = original.indexOf("y =")
    assert(edit.toOriginal(yOffsetRevised).exists(_.start == yOffsetOriginal))
  }

  test("mapping after simple instrumentation") {
    val original = "println(\"Hello, World!\")"
    val revised = "$doc.1\nval $t = println(\"Hello, World!\")\n$doc.2"
    val Some(edit) = TokenEditDistance(mkInput(original), mkInput(revised))

    assert(edit.toOriginalLine(2) == 1)
  }

  test("no mapping for empty input") {
    val Some(edit) = TokenEditDistance(mkInput(""), mkInput(""))

    assert(edit.toOriginal(0).isLeft)
  }

  test("mapping with completely different inputs") {
    val Some(edit) = TokenEditDistance(mkInput("abc"), mkInput("xyz"))

    assert(edit.toOriginal(0).isLeft)
  }

  test("mapping after adding empty line") {
    val original = "val x = 1"
    val revised  = "\nval x = 1"
    val Some(edit) = TokenEditDistance(mkInput(original), mkInput(revised))

    assert(edit.toOriginalLine(2) == 1)
    }
}