package com.olegych.scastie.client

import components._

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, Node}
import org.scalajs.dom.ext._

import scala.scalajs.js
import js.annotation._
import js.{|, UndefOr}
import js.annotation.JSExport

import java.util.UUID

import japgolly.scalajs.react._, extra.router._

@JSExportTopLevel("scastie.ClientMain")
object ClientMain {

  @JSExport
  def main(defaultServerUrl: String): Unit = {
    dom.document.body.className = "scastie"

    val container =
      dom.document.createElement("div").asInstanceOf[dom.raw.HTMLDivElement]
    container.className = "root"
    dom.document.body.appendChild(container)

    val routing = new Routing(defaultServerUrl)

    Router(BaseUrl.fromWindowOrigin_/, routing.config)().renderIntoDOM(
      container
    )

    ()
  }

  @JSExport
  def signal(instrumentations: String,
             attachedDoms: js.Array[HTMLElement],
             rawId: String): Unit = {
    Global.signal(instrumentations, attachedDoms, rawId)
  }

  @JSExport
  def error(er: js.Error, rawId: String): Unit = {
    Global.error(er, rawId)
  }

  @JSExport
  def embedded(selector: String | Node,
               options: UndefOr[EmbeddedOptionsJs],
               defaultServerUrl: String): Unit = {

    val nodes =
      (selector: Any) match {
        case cssSelector: String =>
          dom.document.querySelectorAll(cssSelector).toList
        case node: Node =>
          List(node)
      }

    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJs(defaultServerUrl))
        .getOrElse(EmbeddedOptions.empty(defaultServerUrl))

    if (nodes.nonEmpty) {
      val baseUrl = embeddedOptions.serverUrl

      val link = dom.document
        .createElement("link")
        .asInstanceOf[dom.raw.HTMLLinkElement]

      link.`type` = "text/css"
      link.rel = "stylesheet"
      link.href = baseUrl + "/public/app.css"

      dom.document.getElementsByTagName("head")(0).appendChild(link)
    }

    nodes.foreach {
      case node: dom.raw.HTMLElement => {
        val container = dom.document
          .createElement("div")
          .asInstanceOf[dom.raw.HTMLDivElement]

        container.className = "root embedded"

        val embeddedOptions0 =
          if (node.textContent.isEmpty) {
            embeddedOptions
          } else {
            embeddedOptions.setCode(node.textContent)
          }

        Scastie(
          scastieId = UUID.randomUUID(),
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
}
