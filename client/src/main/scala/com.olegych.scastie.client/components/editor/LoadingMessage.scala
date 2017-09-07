package com.olegych.scastie.client.components.editor

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import codemirror.{Editor => CMEditor}
import codemirror.CodeMirror.{Pos => CMPosition}

private[editor] class LoadingMessage() {
  private val message = {
    val ul = dom.document
      .createElement("ul")
      .asInstanceOf[HTMLElement]
    ul.className = ul.className.concat(" CodeMirror-hints loading-message")
    ul.style.opacity = "0"

    val li = dom.document.createElement("li").asInstanceOf[HTMLElement]
    li.className = li.className.concat("CodeMirror-hint")

    val span = dom.document.createElement("span").asInstanceOf[HTMLElement]
    span.className = span.className.concat("cm-def")
    span.innerHTML = "Loading..."

    li.appendChild(span)
    ul.appendChild(li)

    ul
  }

  def hide(): Unit = {
    message.style.opacity = "0"
  }

  def show(editor: CMEditor, pos: CMPosition): Unit = {
    editor.addWidget(pos, message, scrollIntoView = true)
    message.style.opacity = "1"
  }

  def isVisible: Boolean = message.style.opacity == "1"
}
