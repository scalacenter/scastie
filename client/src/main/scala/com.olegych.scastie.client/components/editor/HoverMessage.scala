package com.olegych.scastie.client.components.editor

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import codemirror.CodeMirror

private[editor] class HoverMessage() {

  private val message = dom.document
    .createElement("div")
    .asInstanceOf[HTMLElement]

  private val tooltip =
    dom.document.createElement("div").asInstanceOf[HTMLElement]
  tooltip.className = tooltip.className.concat(" CodeMirror-hover-tooltip")
  tooltip.appendChild(message)
  dom.document.body.appendChild(tooltip)

  private var node: Option[HTMLElement] = None

  def hideTooltip(e: dom.Event): Unit = {
    CodeMirror.off(dom.document, "mouseout", hideTooltip)
    if (node.isDefined)
      node.get.className = node.get.className.replace(" CodeMirror-hover", "")

    if (tooltip.parentNode != null) {
      tooltip.style.opacity = "0"
      ()
    }
  }

  def position(e: dom.MouseEvent): Unit = {
    if (tooltip.style.opacity == null) {
      CodeMirror.off(dom.document, "mousemove", position)
    }
    tooltip.style.top = Math
      .max(0, e.clientY - tooltip.offsetHeight - 5) + "px"
    tooltip.style.left = (e.clientX + 5) + "px"
  }

  def show(nodeElement: HTMLElement, messageString: String): Unit = {
    node = Some(nodeElement)
    node.get.className = node.get.className.concat(" CodeMirror-hover")
    message.innerHTML = messageString
    CodeMirror.on(dom.document, "mousemove", position)
    CodeMirror.on(dom.document, "mouseout", hideTooltip)
    if (tooltip.style.opacity != null) {
      tooltip.style.opacity = "1"
    }
  }

  def updateMessage(messageString: String): Unit = {
    message.innerHTML = messageString
  }

  def getMessage: String = message.innerHTML
}