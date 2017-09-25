package com.olegych.scastie.client

import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.client.components._

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLElement, Node}
import org.scalajs.dom.ext._

import scala.scalajs.js
import js.annotation._
import js.{|, UndefOr}
import js.annotation.JSExport

import java.util.UUID

import japgolly.scalajs.react._, extra.router._

@js.native
@JSGlobal("scastie.Settings")
object Settings extends js.Object {
  val defaultServerUrl: String = js.native
}

/* Entry point for the website
 */
@JSExportTopLevel("scastie.ScastieMain")
object ScastieMain {
  @JSExport
  def main(): Unit = {
    dom.document.body.className = "scastie"

    val container =
      dom.document.createElement("div").asInstanceOf[dom.raw.HTMLDivElement]
    container.className = "scastie"
    dom.document.body.appendChild(container)

    val routing = new Routing(Settings.defaultServerUrl)

    Router(BaseUrl.fromWindowOrigin_/, routing.config)().renderIntoDOM(
      container
    )

    ()
  }
}

/* Entry point for Scala.js runtime
 */
@JSExportTopLevel("scastie.ClientMain")
object ClientMain {

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
}

/* Entry point for ressource embedding and code embedding
 */
object ScastieEmbedded {

  @JSExportTopLevel("scastie.Embedded")
  def embedded(selector: UndefOr[String | Node],
               options: UndefOr[EmbeddedOptionsJs]): Unit = {

    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJs(Settings.defaultServerUrl))
        .getOrElse(EmbeddedOptions.empty(Settings.defaultServerUrl))

    println("embedded defaultServerUrl: " + Settings.defaultServerUrl)
    println("embedded options: " + options)
    println("embedded embeddedOptions: " + embeddedOptions)

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
      case node: dom.raw.HTMLElement => {
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

  @JSExportTopLevel("scastie.EmbeddedRessource")
  def embeddedRessource(options: UndefOr[EmbeddedRessourceOptionsJs]): Unit = {
    val embeddedOptions =
      options.toOption
        .map(EmbeddedOptions.fromJsRessource(Settings.defaultServerUrl))
        .getOrElse(EmbeddedOptions.empty(Settings.defaultServerUrl))

    println("embeddedRessource defaultServerUrl: " + Settings.defaultServerUrl)
    println("embeddedRessource options: " + options)
    println("embeddedRessource embeddedOptions: " + embeddedOptions)

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
      .asInstanceOf[dom.raw.HTMLLinkElement]

    link.`type` = "text/css"
    link.rel = "stylesheet"
    link.href = baseUrl + "/public/embedded.css"

    dom.document.getElementsByTagName("head")(0).appendChild(link)
  }

  def renderScastie(embeddedOptions: EmbeddedOptions,
                            snippetId: Option[SnippetId]): HTMLElement = {

    val container = dom.document
      .createElement("div")
      .asInstanceOf[dom.raw.HTMLDivElement]

    container.className = "scastie embedded"

    Scastie(
      scastieId = UUID.randomUUID(),
      router = None,
      snippetId = None,
      oldSnippetId = None,
      embedded = Some(embeddedOptions),
      targetType = None
    ).render.renderIntoDOM(container)

    container
  }
}