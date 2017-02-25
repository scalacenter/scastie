package com.olegych.scastie
package client

import api._

import org.scalajs.dom
import org.scalajs.dom.raw.Node
import org.scalajs.dom.ext._

import scala.scalajs.js
import js.{|, UndefOr, JSApp}
import js.annotation.{JSExport, ScalaJSDefined}

import japgolly.scalajs.react._, extra.router._

sealed trait Page
case object Home extends Page
case object Embeded extends Page
case class Snippet(snippedId: SnippetId) extends Page
case class EmbeddedSnippet(snippedId: SnippetId) extends Page {
  def toSnippet = Snippet(snippedId)
}

object ClientMain extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    val alpha = string("[a-zA-Z0-9]*")

    val snippetId = (alpha.option / alpha).xmap{ case (user, uuid) =>
      SnippetId(uuid, user)
    }(snippetId => (snippetId.user, snippetId.base64UUID))

    (
      trimSlashes
        | staticRoute(root, Home) ~> renderR(renderAppDefault)
        | dynamicRouteCT(snippetId.caseClass[Snippet]) ~> dynRenderR(renderAppSnippet)
        | dynamicRouteCT(embedded / snippetId.caseClass[EmbeddedSnippet]) ~> dynRenderR(renderAppSnippetEmbedded)
    ).notFound(redirectToPage(Home)(Redirect.Replace)).renderWith(layout)
  }

  def renderAppDefault(router: RouterCtl[Page]) =
    App(
      App.Props(
        router = Some(router),
        snippet = None,
        embedded = None
      ))

  def renderAppSnippet(snippet: Snippet, router: RouterCtl[Page]) =
    App(
      App.Props(
        router = Some(router),
        snippet = Some(snippet),
        embedded = None
      ))

  def renderAppDefaultEmbedded(router: RouterCtl[Page]) =
    App(
      App.Props(
        router = Some(router),
        snippet = None,
        embedded = Some(EmbededOptions.empty)
      ))

  def renderAppSnippetEmbedded(embeddedSnippet: EmbeddedSnippet,
                               router: RouterCtl[Page]) = {
    val snippet = Some(embeddedSnippet.toSnippet)
    App(
      App.Props(
        router = Some(router),
        snippet = snippet,
        embedded = Some(EmbededOptions.empty)
      ))
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()

  @JSExport
  override def main(): Unit = {
    val isMac = dom.window.navigator.userAgent.contains("Mac")

    dom.document.body.className =
      if (isMac) "mac"
      else "pc"

    val container =
      dom.document.createElement("div").asInstanceOf[dom.raw.HTMLDivElement]
    container.className = "root"
    dom.document.body.appendChild(container)

    ReactDOM.render(
      Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)(),
      container
    )

    ()
  }

  @JSExport
  def embedded(selector: String | Node,
               options: UndefOr[EmbededOptionsJs]): Unit = {
    val nodes =
      (selector: Any) match {
        case cssSelector: String => {
          dom.document.querySelectorAll(cssSelector).toList
        }
        case node: Node => {
          List(node)
        }
      }
    val embeddedOptions =
      options.toOption
        .map(EmbededOptions.fromJs)
        .getOrElse(EmbededOptions.empty)

    nodes.foreach {
      case node: dom.raw.HTMLElement =>
        val container = dom.document.createElement("div")

        val embeddedOptions0 =
          if (node.textContent.isEmpty || embeddedOptions.hasCode)
            embeddedOptions
          else embeddedOptions.setCode(node.textContent)

        ReactDOM.render(
          App(
            App.Props(
              router = None,
              snippet = None,
              embedded = Some(embeddedOptions0)
            )),
          container
        )

        node.parentNode.insertBefore(container, node.nextSibling)
        node.style.display = "none"
    }
  }
}

@ScalaJSDefined
trait EmbededOptionsJs extends js.Object {
  val base64UUID: UndefOr[String]
  val user: UndefOr[String]
  val worksheetMode: UndefOr[Boolean]
  val code: UndefOr[String]
  val targetType: UndefOr[String]
  val scalaVersion: UndefOr[String]
  val sbtConfig: UndefOr[String]
}

object EmbededOptions {
  def empty: EmbededOptions = EmbededOptions(None, None)
  def fromJs(options: EmbededOptionsJs): EmbededOptions = {
    import options._

    EmbededOptions(
      inputs = code.toOption.map(c => Inputs.default.copy(code = c)),
      snippetId = base64UUID.toOption.map(uuid => SnippetId(uuid, user.toOption))
    )
  }
}

case class EmbededOptions(snippetId: Option[SnippetId], inputs: Option[Inputs]) {
  def hasCode: Boolean = inputs.map(!_.code.isEmpty).getOrElse(false)
  def setCode(code: String): EmbededOptions = {
    val inputs0 = inputs.getOrElse(Inputs.default)
    copy(inputs = Some(inputs0.copy(code = code)))
  }
}
