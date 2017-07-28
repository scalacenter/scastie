package com.olegych.scastie

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.Editor

import upickle.default.ReadWriter

import org.scalajs.dom.raw.HTMLElement

import japgolly.scalajs.react.extra.Reusability

package object client {
  // type AttachedDoms = Map[String, HTMLElement]

  case class AttachedDoms(v: Map[String, HTMLElement]) {
    def get(key: String): Option[HTMLElement] = v.get(key)
  }

  def dontSerialize[T](v: T): ReadWriter[T] = {
    import upickle.Js
    ReadWriter[T](_ => Js.Null, { case _ => v })
  }

  implicit val reusability: Reusability[View] =
    Reusability.by_==

  implicit val attachedDomsReuse: Reusability[AttachedDoms] =
    Reusability.byRef ||
      Reusability.by(_.v.keys.toSet)

  implicit val instrumentationReuse: Reusability[Set[Instrumentation]] =
    Reusability.byRefOr_==

  implicit val compilationInfosReuse: Reusability[Set[Problem]] =
    Reusability.byRefOr_==

  implicit val runtimeErrorReuse: Reusability[Option[RuntimeError]] =
    Reusability.byRefOr_==

  implicit val completionsReuse: Reusability[List[Completion]] =
    Reusability.byRefOr_==

  implicit val editorReuse: Reusability[Editor] =
    Reusability.byRef ||
      (
        Reusability.by((_: Editor).attachedDoms) &&
          Reusability.by((_: Editor).instrumentations) &&
          Reusability.by((_: Editor).compilationInfos) &&
          Reusability.by((_: Editor).runtimeError) &&
          Reusability.by((_: Editor).completions)
      )

  def dontSerializeOption[T]: ReadWriter[Option[T]] = dontSerialize(None)

  def dontSerializeList[T]: ReadWriter[List[T]] = dontSerialize(List())

  def dontSerializeMap[K, V]: ReadWriter[Map[K, V]] = dontSerialize(Map())
}
