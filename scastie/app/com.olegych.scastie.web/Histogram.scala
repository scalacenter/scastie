package com.olegych.scastie
package web

import System.{lineSeparator => nl}

object Histogram {
  def apply[T: Ordering](xs: Seq[T]): Histogram[T] = {
    val data = xs.groupBy(x => x).mapValues(v => (v.size.toDouble / xs.size.toDouble))
    new Histogram(data)
  }
}

class Histogram[T: Ordering](protected val data: Map[T, Double]) {
  def distance(other: Histogram[T]): Double = {
    val m1 = data
    val m2 = other.data

    val km1 = m1.keySet
    val km2 = m2.keySet

    (km2 -- km1).map(_ => 0).sum +  // missing in m1
    (km1 -- km2).map(_ => 0).sum +  // missing in m2
    (km1.intersect(km2)).map(k =>   // in m1 and m2
      Math.abs(m1(k) - m2(k))
    ).sum 
  }

  override def toString: String = {
    def padLeft(length: Int, value: String): String =
            String.format("%1$" + length + "s", value)

    data.toList
     .sortBy{ case (k, v) => (v, k)}
     .reverse
     .map{ 
        case (k, v) => 
          val pp = Math.floor(100 * v).toInt
          val ppp = padLeft(2, pp.toString)
          s"$k ($ppp) ${"*" * pp}"
      }.mkString(nl)
  }
}
