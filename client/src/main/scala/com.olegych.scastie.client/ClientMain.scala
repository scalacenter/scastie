package com.olegych.scastie.client

import components._

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, Node}
import org.scalajs.dom.ext._

import scala.scalajs.js
import js.annotation._
import js.{|, UndefOr, JSApp}
import js.annotation.JSExport

import japgolly.scalajs.react._, extra.router._

@JSExportTopLevel("scastie.ClientMain")
object ClientMain {

  @JSExport
  def main(): Unit = {
    val isMac = dom.window.navigator.userAgent.contains("Mac")

    dom.document.body.className =
      if (isMac) "mac"
      else "pc"

    val container =
      dom.document.createElement("div").asInstanceOf[dom.raw.HTMLDivElement]
    container.className = "root"
    dom.document.body.appendChild(container)

    Router(BaseUrl.fromWindowOrigin_/, Routing.config)().renderIntoDOM(
      container
    )

    ()
  }

  @JSExport
  def signal(instrumentations: String,
             attachedDoms: js.Array[HTMLElement]): Unit = {
    Global.signal(instrumentations, attachedDoms)
  }

  @JSExport
  def error(er: js.Error): Unit = {
    Global.error(er)
  }

  @JSExport
  def embedded(selector: String | Node,
               options: UndefOr[EmbeddedOptionsJs]): Unit = {
    val nodes =
      (selector: Any) match {
        case cssSelector: String =>
          dom.document.querySelectorAll(cssSelector).toList
        case node: Node =>
          List(node)
      }

    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJs)
        .getOrElse(EmbeddedOptions.empty)

    nodes.foreach {
      case node: dom.raw.HTMLElement =>
        val container = dom.document
          .createElement("div")
          .asInstanceOf[dom.raw.HTMLDivElement]
        container.className = "root"

        val embeddedOptions0 =
          if (node.textContent.isEmpty || embeddedOptions.hasCode)
            embeddedOptions
          else embeddedOptions.setCode(node.textContent)

        Scastie(
          router = None,
          snippetId = None,
          oldSnippetId = None,
          embedded = Some(embeddedOptions0),
          targetType = None
        ).render.renderIntoDOM(container)

        node.parentNode.insertBefore(container, node.nextSibling)
        node.style.display = "none"
    }
  }
}
