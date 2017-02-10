package com.olegych.scastie
package web

trait TestUtils {
  implicit class IntExtension(n: Int) {
    def *(v: String): Seq[String] = List.fill(n)(v)
  }
}

