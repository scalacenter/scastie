package com.olegych.scastie
package web

import org.scalatest.FunSuite

class HistogramTest extends FunSuite with TestUtils {
  test("0 distance") {
    val empty = Histogram.empty[Int]
    assert(empty.distance(empty) == 0)
  }

  test("distance no intersection") {
    val h1 = Histogram(1)
    val h2 = Histogram(2)

    assert(Math.floor(h1.distance(h2)).toInt == 2)
  }

  test("distance equal") {
    val h1 = Histogram(1, 2, 3)
    val h2 = h1

    assert(Math.floor(h1.distance(h2)).toInt == 0)
  }

  test("distance overlap") {
    val h1 = Histogram(1,2,3,4  )
    val h2 = Histogram(  2,3,4,5)

    assert(Math.floor(h1.distance(h2) * 100).toInt == 50)
  }

  test("distance sample") {
    val h1 = histogram(
      10 * "c1",
      5 * "c2",
      1 * "c7",
      1 * "c6",
      1 * "c5",
      1 * "c4",
      1 * "c3"
    )
    val h2 = histogram(
      2 * "c1",
      1 * "c2",
      1 * "c3",
      1 * "c4"
    )

    assert(Math.floor(h1.distance(h2) * 100).toInt == 35)
  }

  def histogram(xs: Seq[String]*): Histogram[String] = {
    Histogram.fromSeq(xs.flatten)
  }
}
