package org.scastie

import io.circe._
import org.scalajs.dom.window

package object client {

  def dontSerialize[T](fallback: T): Codec[T] = new Codec[T] {
    def apply(c: HCursor): Decoder.Result[T] = Right(fallback)
    def apply(a: T): Json = io.circe.Json.Null
  }

  def dontSerializeOption[T]: Codec[T] = new Codec[T] {
    def apply(c: HCursor): Decoder.Result[T] = Right(null.asInstanceOf[T])
    def apply(a: T): Json = io.circe.Json.Null
  }

  def dontSerializeList[T]: Codec[List[T]] = dontSerialize(List())

  val isMac: Boolean = window.navigator.userAgent.contains("Mac")
  val isMobile: Boolean = "Android|webOS|Mobi|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Samsung".r.unanchored
    .matches(window.navigator.userAgent)
}
