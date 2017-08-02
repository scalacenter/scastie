package com.olegych.scastie

import play.api.libs.json._

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.Editor

import japgolly.scalajs.react.extra.Reusability

import org.scalajs.dom.window

package object client {

  def dontSerialize[T](fallback: T): Format[T] = new Format[T] {
    def writes(v: T): JsValue = JsNull
    def reads(json: JsValue): JsResult[T] = JsSuccess(fallback)
  }

  def dontSerializeOption[T]: Format[Option[T]] =
    dontSerialize(None)

  def dontSerializeList[T]: Format[List[T]] =
    dontSerialize(List())

  val isMac = window.navigator.userAgent.contains("Mac")
  val ctrl = if (isMac) "⌘" else "Ctrl"

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
}
