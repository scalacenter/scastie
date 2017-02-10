package com.olegych.scastie
package balancer
package utils

// Ordering only for debug purposes
object Multiset{
  def apply[T: Ordering](xs: Seq[T]): Multiset[T] =
    Multiset(xs.groupBy(x => x).map { case (k, vs) => (k, vs.size)})
}
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
