package com.olegych.scastie.client

import java.util.UUID

import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.client.components._
import japgolly.scalajs.react.component.Generic
import japgolly.scalajs.react.extra.router._
import org.scalajs.dom
import org.scalajs.dom.{HTMLElement, HTMLDivElement, HTMLLinkElement, Node}

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, _}
import scala.scalajs.js.{UndefOr, |}

@js.native
@JSGlobal("ScastieSettings")
object Settings extends js.Object {
  val defaultServerUrl: String = js.native
}

@JSExportTopLevel("scastie")
object Exports {
  @JSExport
  val ScastieMain = com.olegych.scastie.client.ScastieMain
  @JSExport
  val ClientMain = com.olegych.scastie.client.ScastieClientMain
  @JSExport
  def Embedded(selector: UndefOr[String | Node], options: UndefOr[EmbeddedOptionsJs]) = ScastieEmbedded.embedded(selector, options)
  @JSExport
  def EmbeddedResource(options: UndefOr[EmbeddedResourceOptionsJs]) = ScastieEmbedded.embeddedResource(options)
}
/* Entry point for the website
 */
object ScastieMain {
  @JSExport
  def main(): Unit = {
    dom.document.body.className = "scastie"

    val container =
      dom.document.createElement("div").asInstanceOf[HTMLDivElement]
    container.className = "scastie"
    dom.document.body.appendChild(container)

    val routing = new Routing(Settings.defaultServerUrl)

    val router = Router(BaseUrl.fromWindowOrigin_/, routing.config)
    Generic
      .toComponentCtor(router)
      .apply()
      .renderIntoDOM(
        container
      )

    ()
  }
}

/* Entry point for Scala.js runtime
 */
object ScastieClientMain {
  @JSExport
  def signal(instrumentations: String, attachedDoms: js.Array[HTMLElement], rawId: String): Unit = {
    Global.signal(instrumentations, attachedDoms, rawId)
  }

  @JSExport
  def error(er: js.Error, rawId: String): Unit = {
    Global.error(er, rawId)
  }
}

/* Entry point for ressource embedding and code embedding
 */
object ScastieEmbedded {
  def embedded(selector: UndefOr[String | Node], options: UndefOr[EmbeddedOptionsJs]): Unit = {

    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJs(Settings.defaultServerUrl))
        .getOrElse(EmbeddedOptions.empty(Settings.defaultServerUrl))

    val nodes =
      selector.toOption match {
        case Some(sel) => {
          (sel: Any) match {
            case cssSelector: String =>
              dom.document.querySelectorAll(cssSelector).toList
            case node: Node =>
              List(node)
          }
        }
        case None => List()
      }

    if (nodes.nonEmpty) {
      addStylesheet(embeddedOptions.serverUrl)
    }

    nodes.foreach {
      case node: HTMLElement => {
        val embeddedOptions0 =
          if (node.textContent.isEmpty) {
            embeddedOptions
          } else {
            embeddedOptions.setCode(node.textContent)
          }

        val container = renderScastie(
          embeddedOptions = embeddedOptions0,
          snippetId = None
        )

        node.parentNode.insertBefore(container, node.nextSibling)
        node.style.display = "none"
      }
    }
  }

  def embeddedResource(options: UndefOr[EmbeddedResourceOptionsJs]): Unit = {
    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJsRessource(Settings.defaultServerUrl))
        .getOrElse(EmbeddedOptions.empty(Settings.defaultServerUrl))

    val container =
      renderScastie(
        embeddedOptions = embeddedOptions,
        snippetId = embeddedOptions.snippetId
      )

    embeddedOptions.injectId match {
      case Some(id) => {
        Option(dom.document.querySelector("#" + id)) match {
          case Some(element) => {
            addStylesheet(embeddedOptions.serverUrl)
            element.parentNode.replaceChild(container, element)
          }
          case None => {
            sys.error("cannot find injectId: " + id)
          }
        }
      }
      case None => {
        sys.error("injectId is not defined")
      }
    }
  }
  def addStylesheet(baseUrl: String): Unit = {
    val link = dom.document
      .createElement("link")
      .asInstanceOf[HTMLLinkElement]

    link.`type` = "text/css"
    link.rel = "stylesheet"
    link.href = baseUrl + "/public/embedded.css"

    dom.document.getElementsByTagName("head")(0).appendChild(link)
  }

  def renderScastie(embeddedOptions: EmbeddedOptions, snippetId: Option[SnippetId]): HTMLElement = {

    val container = dom.document
      .createElement("div")
      .asInstanceOf[HTMLDivElement]

    container.className = "scastie embedded"

    Scastie(
      scastieId = UUID.randomUUID(),
      router = None,
      snippetId = None,
      oldSnippetId = None,
      embedded = Some(embeddedOptions),
      targetType = None,
      tryLibrary = None,
      code = None,
      inputs = None,
    ).render.renderIntoDOM(container)

    container
  }
}
