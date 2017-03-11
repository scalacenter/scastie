package com.olegych.scastie

import upickle.default.ReadWriter

import org.scalajs.dom.raw.HTMLElement

package object client {
  type AttachedDoms = Map[String, HTMLElement]

  def dontSerialize[T](v: T): ReadWriter[T] = {
    import upickle.Js
    ReadWriter[T](_ => Js.Null, { case _ => v })
  }

  def dontSerializeOption[T]: ReadWriter[Option[T]] = dontSerialize(None)

  def dontSerializeMap[K, V]: ReadWriter[Map[K, V]] = dontSerialize(Map())
}
