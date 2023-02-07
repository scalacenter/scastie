package com.olegych.scastie

import play.api.libs.json._

import org.scalajs.dom.window

package object client {

  def dontSerialize[T](fallback: T): Format[T] = new Format[T] {
    def writes(v: T): JsValue = JsNull
    def reads(json: JsValue): JsResult[T] = JsSuccess(fallback)
  }

  def dontSerializeOption[T]: Format[T] = new Format[T] {
    def writes(v: T): JsValue = JsNull
    def reads(json: JsValue): JsResult[T] = JsSuccess(null.asInstanceOf[T])
  }

  def dontSerializeList[T]: Format[List[T]] =
    dontSerialize(List())

  val isMac: Boolean = window.navigator.userAgent.contains("Mac")
  val isMobile: Boolean = "Android|webOS|Mobi|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Samsung".r.unanchored
    .matches(window.navigator.userAgent)
}
