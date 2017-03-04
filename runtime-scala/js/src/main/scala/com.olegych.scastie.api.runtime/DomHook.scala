package com.olegych.scastie.api
package runtime

import org.scalajs.dom.raw.HTMLElement
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

import scala.collection.mutable.Buffer

trait DomHook {
  private val elements = Buffer.empty[HTMLElement]
  
  def attach(element: HTMLElement): Unit = {
    elements += element
    ()
  }

  @JSExport
  def attachedElements: js.Array[HTMLElement] = elements.to[js.Array] 
}
