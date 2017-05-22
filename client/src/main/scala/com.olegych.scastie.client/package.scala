package com.olegych.scastie

import upickle.default.ReadWriter

import org.scalajs.dom.raw.HTMLElement

import japgolly.scalajs.react.extra.Reusability

package object client {
  type AttachedDoms = Map[String, HTMLElement]

  def dontSerialize[T](v: T): ReadWriter[T] = {
    import upickle.Js
    ReadWriter[T](_ => Js.Null, { case _ => v })
  }

  implicit val reusability: Reusability[View] =
    Reusability.by_==

  def dontSerializeOption[T]: ReadWriter[Option[T]] = dontSerialize(None)

  def dontSerializeMap[K, V]: ReadWriter[Map[K, V]] = dontSerialize(Map())
}
