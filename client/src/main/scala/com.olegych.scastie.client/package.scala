package com.olegych.scastie

import upickle.default.ReadWriter

package object client {
  val console = org.scalajs.dom.console

  def dontSerialize[T](v: T): ReadWriter[T] = {
    import upickle.Js
    ReadWriter[T](_ => Js.Null, { case _ => v })
  }

  def dontSerializeOption[T]: ReadWriter[Option[T]] = dontSerialize(None)

  def dontSerializeMap[K, V]: ReadWriter[Map[K, V]] = dontSerialize(Map())
}
