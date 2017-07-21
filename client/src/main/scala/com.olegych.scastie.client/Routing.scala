package com.olegych.scastie
package client

import api.{SnippetId, SnippetUserPart, ScalaTargetType}
import components._

import japgolly.scalajs.react._, vdom.all._, extra.router._

object Routing {
  val config = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    val alpha = string("[a-zA-Z0-9-]*")

    val targetType = ("?target=" ~ alpha.pmap(
      in => ScalaTargetType.parse(in.toUpperCase)
    )(_.toString))

    val anon = alpha
    val user = (alpha / alpha)
    val userUpdate = (alpha / alpha / int)
    val oldId = int

    (
      trimSlashes
        | staticRoute(root, Home) ~>
          renderR(renderScastieDefault)

        | dynamicRouteCT(targetType.caseClass[TargetTypePage]) ~>
          dynRenderR((page, router) => renderTargetTypePage(page, router))

        | dynamicRouteCT(oldId.caseClass[OldSnippetIdPage]) ~>
          dynRenderR((page, router) => renderOldSnippetIdPage(page, router))

        | dynamicRouteCT(anon.caseClass[AnonymousResource]) ~>
          dynRenderR((page, router) => renderPage(page, router))

        | dynamicRouteCT(user.caseClass[UserResource]) ~>
          dynRenderR((page, router) => renderPage(page, router))

        | dynamicRouteCT(userUpdate.caseClass[UserResourceUpdated]) ~>
          dynRenderR((page, router) => renderPage(page, router))

        | staticRoute(embedded, Embedded) ~>
          renderR(renderScastieDefaultEmbedded)

        | dynamicRouteCT(embedded / anon.caseClass[EmbeddedAnonymousResource]) ~>
          dynRenderR((page, router) => renderPage(page, router))

        | dynamicRouteCT(embedded / user.caseClass[EmbeddedUserResource]) ~>
          dynRenderR((page, router) => renderPage(page, router))

        | dynamicRouteCT(
          embedded / userUpdate.caseClass[EmbeddedUserResourceUpdated]
        ) ~>
          dynRenderR((page, router) => renderPage(page, router))
    ).notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith((page, router) => layout(page, router))
  }

  private def renderScastieDefault(router: RouterCtl[Page]): VdomElement =
    Scastie.default(router).render

  private def renderTargetTypePage(page: TargetTypePage,
                                   router: RouterCtl[Page]): VdomElement =
    Scastie.default(router).copy(targetType = Some(page.targetType)).render

  private def renderOldSnippetIdPage(page: OldSnippetIdPage,
                                     router: RouterCtl[Page]): VdomElement =
    Scastie.default(router).copy(oldSnippetId = Some(page.id)).render

  private def renderScastieDefaultEmbedded(
      router: RouterCtl[Page]
  ): VdomElement =
    Scastie.default(router).copy(embedded = Some(EmbeddedOptions.empty)).render

  private def renderPage(page: ResourcePage,
                         router: RouterCtl[Page]): VdomElement = {
    val defaultEmbedded = Some(EmbeddedOptions.empty)

    val (embedded, snippetId) =
      page match {
        case AnonymousResource(uuid) =>
          (None, SnippetId(uuid, None))

        case UserResource(login, uuid) =>
          (None, SnippetId(uuid, Some(SnippetUserPart(login, None))))

        case UserResourceUpdated(login, uuid, update) =>
          (None, SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))

        case EmbeddedAnonymousResource(uuid) =>
          (defaultEmbedded, SnippetId(uuid, None))

        case EmbeddedUserResource(login, uuid) =>
          (defaultEmbedded, SnippetId(uuid, Some(SnippetUserPart(login, None))))

        case EmbeddedUserResourceUpdated(login, uuid, update) =>
          (defaultEmbedded,
           SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))))

      }

    Scastie(
      router = Some(router),
      snippetId = Some(snippetId),
      oldSnippetId = None,
      embedded = embedded,
      targetType = None
    ).render
  }

  private def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()
}
