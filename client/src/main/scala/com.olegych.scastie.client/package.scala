package com.olegych.scastie

import play.api.libs.json._

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

  val isMac: Boolean = window.navigator.userAgent.contains("Mac")
  val ctrl: String = if (isMac) "⌘" else "Ctrl"
}
