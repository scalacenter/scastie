package com.olegych.scastie
package web

import System.{lineSeparator => nl}

import scala.reflect.ClassTag
import scala.collection.generic._
import scala.collection.mutable.Builder

object Histogram {
  implicit def canBuildFrom[A: Ordering]: CanBuildFrom[Seq[A], A, Histogram[A]] = {
    def builder = Seq.newBuilder[A].mapResult(seq => Histogram.fromSeq(seq))
    new CanBuildFrom[Seq[A], A, Histogram[A]] {
      def apply(): Builder[A, Histogram[A]] = builder
      def apply(from: Seq[A]) = builder
    }
  }
  def empty[T: Ordering]: Histogram[T] = new Histogram[T](Map())
  def fromSeq[T: Ordering](xs: Seq[T]): Histogram[T] = {
    val data = xs.groupBy(x => x).mapValues(v => (v.size.toDouble / xs.size.toDouble))
    new Histogram(data)
  }
  def apply[T: Ordering](xs: T*): Histogram[T] = fromSeq(xs)
}

class Histogram[T: Ordering](protected val data: Map[T, Double]) {
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

  override def toString: String = {
    def padLeft(length: Int, value: String): String =
            String.format("%1$" + length + "s", value)

    val window = 50
    val box = nl + "-" * (window + 10) + nl

    data.toList
     .sortBy{ case (k, v) => (v, k)}
     .reverse
     .map{ 
        case (k, v) => 
          val pp = Math.floor(window * v).toInt
          val ppp = padLeft(2, pp.toString)
          s"$k ($ppp%) ${"*" * pp}"
      }.mkString(box, nl, box)
  }
}
