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

object Page {
  def fromSnippetId(snippetId: SnippetId): ResourcePage = {
    snippetId match {
      case SnippetId(uuid, None) => AnonymousResource(uuid)
      case SnippetId(uuid, Some(SnippetUserPart(login, None))) => UserResource(login, uuid)
      case SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))) => UserResourceUpdated(login, uuid, update)
    }
  }
}

sealed trait Page
case object Home extends Page
case object Embeded extends Page

sealed trait ResourcePage extends Page

case class AnonymousResource(uuid: String) extends ResourcePage
case class UserResource(login: String, uuid: String) extends ResourcePage
case class UserResourceUpdated(login: String, uuid: String, update: Int) extends ResourcePage

case class EmbeddedAnonymousResource(uuid: String) extends ResourcePage
case class EmbeddedUserResource(login: String, uuid: String) extends ResourcePage
case class EmbeddedUserResourceUpdated(login: String, uuid: String, update: Int) extends ResourcePage

object ClientMain extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    val alpha = string("[a-zA-Z0-9]*")

    val anon = alpha
    val user = (alpha / alpha)
    val userUpdate = (alpha / alpha / int)

    (
      trimSlashes
        | staticRoute(root, Home) ~> renderR(renderAppDefault)
        | dynamicRouteCT(anon.caseClass[AnonymousResource]) ~> dynRenderR(renderPage)
        | dynamicRouteCT(user.caseClass[UserResource]) ~> dynRenderR(renderPage)
        | dynamicRouteCT(userUpdate.caseClass[UserResourceUpdated]) ~> dynRenderR(renderPage)

        | staticRoute(embedded, Embeded) ~> renderR(renderAppDefaultEmbedded)
        | dynamicRouteCT(embedded / anon.caseClass[EmbeddedAnonymousResource]) ~> dynRenderR(renderPage)
        | dynamicRouteCT(embedded / user.caseClass[EmbeddedUserResource]) ~> dynRenderR(renderPage)
        | dynamicRouteCT(embedded / userUpdate.caseClass[EmbeddedUserResourceUpdated]) ~> dynRenderR(renderPage)
        
    ).notFound(redirectToPage(Home)(Redirect.Replace)).renderWith(layout)
  }

  def renderAppDefault(router: RouterCtl[Page]) =
    App(
      App.Props(
        router = Some(router),
        snippetId = None,
        embedded = None
      ))

  def renderAppDefaultEmbedded(router: RouterCtl[Page]) =
    App(
      App.Props(
        router = Some(router),
        snippetId = None,
        embedded = Some(EmbededOptions.empty)
      ))

  def renderPage(page: ResourcePage, router: RouterCtl[Page]) = {
    val defaultEmbedded = Some(EmbededOptions.empty)

    val (embedded, snippetId) =
      page match {
        case AnonymousResource(uuid)                          => (None,            SnippetId(uuid, None))
        case UserResource(login, uuid)                        => (None,            SnippetId(uuid, Some(SnippetUserPart(login, None))))
        case UserResourceUpdated(login, uuid, update)         => (None,            SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))
        case EmbeddedAnonymousResource(uuid)                  => (defaultEmbedded, SnippetId(uuid, None))
        case EmbeddedUserResource(login, uuid)                => (defaultEmbedded, SnippetId(uuid, Some(SnippetUserPart(login, None))))
        case EmbeddedUserResourceUpdated(login, uuid, update) => (defaultEmbedded, SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))
      }

    App(
      App.Props(
        router = Some(router),
        snippetId = Some(snippetId),
        embedded = embedded
      )
    )
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
              snippetId = None,
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
  val update: UndefOr[Int]
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
      snippetId = 
        base64UUID.toOption.map(uuid => 
          SnippetId(uuid, user.toOption.map(u => SnippetUserPart(u, update.toOption)))
        )
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
