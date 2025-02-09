package com.olegych.scastie.api
package runtime

import java.util.UUID
import scala.collection.mutable.Buffer
import scala.scalajs.js

import org.scalajs.dom.HTMLElement

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
