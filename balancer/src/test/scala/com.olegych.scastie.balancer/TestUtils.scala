package com.olegych.scastie
package balancer

trait TestUtils {
  implicit class IntExtension(n: Int) {
    def *[T](v: T): Seq[T] = List.fill(n)(v)
  }
}
