package com.olegych.scastie

package object web {
  def multiset[T: Ordering](xs: Seq[T]): Multiset[T] =
    Multiset(xs.groupBy(x => x).map { case (k, vs) => (k, vs.size) })
}

