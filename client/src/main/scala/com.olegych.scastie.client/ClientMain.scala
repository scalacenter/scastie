package com.olegych.scastie
package client

import org.scalajs.dom

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, extra.router._

sealed trait Page
case object Home extends Page
case object Embeded extends Page
case class Snippet(id: Long) extends Page
case class EmbeddedSnippet(id: Long) extends Page {
  def toSnippet = Snippet(id)
}

object ClientMain extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    (
      trimSlashes
      | staticRoute(root, Home) ~> renderR(renderAppDefault)
      | dynamicRouteCT(long.caseClass[Snippet]) ~> dynRenderR(renderAppSnippet)
      | staticRoute(embedded, Embeded) ~> renderR(renderAppDefaultEmbedded)
      | dynamicRouteCT((embedded / long).caseClass[EmbeddedSnippet]) ~> dynRenderR(renderAppSnippetEmbedded)
    )
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
  }

  def renderAppDefault(router: RouterCtl[Page]) = 
    App(router, snippet = None, embedded = false)
  
  def renderAppSnippet(snippet: Snippet, router: RouterCtl[Page]) =
    App(router, Some(snippet), embedded = false)
  
  def renderAppDefaultEmbedded(router: RouterCtl[Page]) = 
    App(router, snippet = None, embedded = true)

  def renderAppSnippetEmbedded(embeddedSnippet: EmbeddedSnippet, router: RouterCtl[Page]) = 
    App(router, Some(embeddedSnippet.toSnippet), embedded = true)

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
}
