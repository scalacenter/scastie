package com.olegych.scastie.api
package runtime

import org.scalajs.dom.HTMLElement
import scala.scalajs.js

import scala.collection.mutable.Buffer

import java.util.UUID

trait DomHook {
  private val elements = Buffer.empty[HTMLElement]

  def attach(element: HTMLElement): UUID = {
    val uuid = UUID.randomUUID()
    element.setAttribute("uuid", uuid.toString)
    elements += element
    uuid
  }

  def attachedElements: js.Array[HTMLElement] = js.Array(elements.toSeq: _*)
}
