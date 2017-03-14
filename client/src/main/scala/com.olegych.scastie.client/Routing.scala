package com.olegych.scastie
package client

import api.{SnippetId, SnippetUserPart}

import japgolly.scalajs.react._, extra.router._

object Routing {
  val config = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    val alpha = string("[a-zA-Z0-9]*")

    val anon = alpha
    val user = (alpha / alpha)
    val userUpdate = (alpha / alpha / int)

    (
      trimSlashes
        | staticRoute(root, Home) ~> renderR(renderAppDefault)
        | dynamicRouteCT(anon.caseClass[AnonymousResource]) ~> dynRenderR(
          renderPage)
        | dynamicRouteCT(user.caseClass[UserResource]) ~> dynRenderR(
          renderPage)
        | dynamicRouteCT(userUpdate.caseClass[UserResourceUpdated]) ~> dynRenderR(
          renderPage)

        | staticRoute(embedded, Embeded) ~> renderR(renderAppDefaultEmbedded)
        | dynamicRouteCT(embedded / anon.caseClass[EmbeddedAnonymousResource]) ~> dynRenderR(
          renderPage)
        | dynamicRouteCT(embedded / user.caseClass[EmbeddedUserResource]) ~> dynRenderR(
          renderPage)
        | dynamicRouteCT(
          embedded / userUpdate
            .caseClass[EmbeddedUserResourceUpdated]) ~> dynRenderR(renderPage)
    ).notFound(redirectToPage(Home)(Redirect.Replace)).renderWith(layout)
  }

  def renderAppDefault(router: RouterCtl[Page]) =
    App(
      AppProps(
        router = Some(router),
        snippetId = None,
        embedded = None
      ))

  def renderAppDefaultEmbedded(router: RouterCtl[Page]) =
    App(
      AppProps(
        router = Some(router),
        snippetId = None,
        embedded = Some(EmbededOptions.empty)
      ))

  def renderPage(page: ResourcePage, router: RouterCtl[Page]) = {
    val defaultEmbedded = Some(EmbededOptions.empty)

    val (embedded, snippetId) =
      page match {
        case AnonymousResource(uuid) => (None, SnippetId(uuid, None))
        case UserResource(login, uuid) =>
          (None, SnippetId(uuid, Some(SnippetUserPart(login, None))))
        case UserResourceUpdated(login, uuid, update) =>
          (None, SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))
        case EmbeddedAnonymousResource(uuid) =>
          (defaultEmbedded, SnippetId(uuid, None))
        case EmbeddedUserResource(login, uuid) =>
          (defaultEmbedded,
           SnippetId(uuid, Some(SnippetUserPart(login, None))))
        case EmbeddedUserResourceUpdated(login, uuid, update) =>
          (defaultEmbedded,
           SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))
      }

    App(
      AppProps(
        router = Some(router),
        snippetId = Some(snippetId),
        embedded = embedded
      )
    )
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()
}
