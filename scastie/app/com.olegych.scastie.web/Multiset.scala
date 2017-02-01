package com.olegych.scastie
package web

// Ordering only for debug purposes
case class Multiset[T: Ordering](inner: Map[T, Int]) {
  override def toString: String = {
    val size = inner.values.sum

    inner.toList
      .sortBy{ case (k, v) => (v, k)}
      .reverse
      .map { case (k, v) => s"$k($v)"}
      .mkString("Multiset(", ", ", s") {$size}")
  }
}
