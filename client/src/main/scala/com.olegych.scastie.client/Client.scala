package com.olegych.scastie
package client

import org.scalajs.dom

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, extra.router._

sealed trait Page
case object Home extends Page
case class Snippet(id: Long) extends Page

object Client extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    (trimSlashes
      | staticRoute(root, Home) ~> renderR(renderAppDefault)
      | dynamicRouteCT(long.caseClass[Snippet]) ~> dynRenderR(renderAppSnippet))
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
  }

  def renderAppDefault(router: RouterCtl[Page]) = App(router, snippet = None)
  def renderAppSnippet(snippet: Snippet, router: RouterCtl[Page]) =
    App(router, Some(snippet))

  def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()

  @JSExport
  override def main(): Unit = {
    val isMac = dom.window.navigator.userAgent.contains("Mac")

    dom.document.body.className =
      if (isMac) "mac"
      else "pc"

    val cont =
      dom.document.createElement("div").asInstanceOf[dom.raw.HTMLDivElement]
    cont.className = "root"
    dom.document.body.appendChild(cont)

    ReactDOM.render(
      Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)(),
      cont)

    ()
  }
}
