package com.olegych.scastie
package balancer
package utils

import scala.collection.generic._
import scala.collection.mutable.Builder

object Histogram {
  implicit def canBuildFrom[A]: CanBuildFrom[Seq[A], A, Histogram[A]] = {
    def builder = Seq.newBuilder[A].mapResult(seq => Histogram.fromSeq(seq))
    new CanBuildFrom[Seq[A], A, Histogram[A]] {
      def apply(): Builder[A, Histogram[A]] = builder
      def apply(from: Seq[A]) = builder
    }
  }
  def empty[T]: Histogram[T] = new Histogram[T](Map())
  def fromSeq[T](xs: Seq[T]): Histogram[T] = {
    val data =
      xs.groupBy(x => x).mapValues(v => (v.size.toDouble / xs.size.toDouble))
    new Histogram(data)
  }
  def apply[T](xs: T*): Histogram[T] = fromSeq(xs)
}

class Histogram[T](protected val data: Map[T, Double]) {
  def distance(other: Histogram[T]): Double = {
    val m1 = data
    val m2 = other.data

    val km1 = m1.keySet
    val km2 = m2.keySet

    val missingM1 = (km2 -- km1).map(k => m2(k)).sum
    val missingM2 = (km1 -- km2).map(k => m1(k)).sum
    val intersect = (km1.intersect(km2)).map(k => Math.abs(m1(k) - m2(k))).sum

    missingM1 + missingM2 + intersect
  }
}
