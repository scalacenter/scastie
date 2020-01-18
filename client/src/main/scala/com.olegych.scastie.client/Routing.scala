package com.olegych.scastie.client

import com.olegych.scastie.api.{Project, ScalaDependency, ScalaTarget, ScalaTargetType, ScalaVersions, SnippetId, SnippetUserPart}
import com.olegych.scastie.client.components._
import japgolly.scalajs.react._
import vdom.all._
import extra.router._
import java.util.UUID

class Routing(defaultServerUrl: String) {
  val config: RouterConfig[Page] = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val embedded = "embedded"

    val alpha = string("[a-zA-Z0-9-]+")
    val snippetId = string("[a-zA-Z0-9-]{22}")

    val targetType = "?target=" ~ alpha.pmap(
      in => ScalaTargetType.parse(in.toUpperCase)
    )(_.toString)

    def parseTryLibrary(params: String) = {

      val map =
        params
          .split("&")
          .toList
          .map(_.split("=").toList)
          .map {
            case List(k, v) => (k, v)
          }
          .toMap

      (
        map.get("g"),
        map.get("a"),
        map.get("v"),
        map.get("o"),
        map.get("r"),
      ) match {
        case (Some(g), Some(a), Some(v), o, r) =>
          val target =
            map.get("t").flatMap(ScalaTargetType.parse) match {
              case Some(ScalaTargetType.JVM) =>
                map.get("sv").map(sv => ScalaTarget.Jvm(ScalaVersions.find(sv)))

              case Some(ScalaTargetType.JS) =>
                (map.get("sv"), map.get("sjsv")) match {
                  case (Some(sv), sjsv) =>
                    Some(ScalaTarget.Js(ScalaVersions.find(sv), sjsv.getOrElse(ScalaTarget.Js.default.scalaJsVersion)))
                  case _ => None
                }

              case _ => None
            }

          val project = (r, o) match {
            case (Some(r), Some(o)) => Project(organization = o, repository = r, None, Nil)
            case _                  => Project("", "", None, Nil)
          }
          target.map(t => (ScalaDependency(g, a, t, v), project))
        case _ => None
      }
    }

    def renderTryLibrary(dep: (ScalaDependency, Project)): String = {
      import dep._1._

      val tm: Map[String, String] =
        target match {
          case ScalaTarget.Jvm(sv)      => Map("sv" -> sv)
          case ScalaTarget.Js(sv, sjsv) => Map("sv" -> sv, "sjsv" -> sjsv)
          case _                        => Map()
        }

      val gavro: Map[String, String] =
        Map(
          "g" -> groupId,
          "a" -> artifact,
          "v" -> version,
          "r" -> dep._2.repository,
          "o" -> dep._2.organization
        )

      (gavro ++ tm)
        .map {
          case (k, v) => k + "=" + v
        }
        .mkString("?", "&", "")
    }

    val tryLibrary = "?" ~ remainingPath.pmap(parseTryLibrary)(renderTryLibrary)

    val anon = snippetId
    val user = alpha / snippetId
    val userUpdate = alpha / snippetId / int
    val oldId = int

    (
      trimSlashes
        | staticRoute(root, Home) ~>
          renderR(renderScastieDefault)

        | dynamicRouteCT(targetType.caseClass[TargetTypePage]) ~>
          dynRenderR((page, router) => renderTargetTypePage(page, router))

        | dynamicRouteCT("try" ~ tryLibrary.caseClass[TryLibraryPage]) ~>
          dynRenderR((page, router) => renderTryLibraryPage(page, router))

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
    ).notFound(redirectToPage(Home)(SetRouteVia.HistoryReplace))
      .renderWith((page, router) => layout(page, router))
  }

  private def renderScastieDefault(router: RouterCtl[Page]): VdomElement = {

    Scastie.default(router).render
  }

  private def renderTargetTypePage(page: TargetTypePage, router: RouterCtl[Page]): VdomElement = {

    Scastie.default(router).copy(targetType = Some(page.targetType)).render
  }

  private def renderTryLibraryPage(page: TryLibraryPage, router: RouterCtl[Page]): VdomElement = {

    Scastie.default(router).copy(tryLibrary = Some(page.dependency)).render
  }

  private def renderOldSnippetIdPage(page: OldSnippetIdPage, router: RouterCtl[Page]): VdomElement = {

    Scastie.default(router).copy(oldSnippetId = Some(page.id)).render
  }

  private def renderScastieDefaultEmbedded(
      router: RouterCtl[Page]
  ): VdomElement =
    Scastie
      .default(router)
      .copy(embedded = Some(EmbeddedOptions.empty(defaultServerUrl)))
      .render

  private def renderPage(page: ResourcePage, router: RouterCtl[Page]): VdomElement = {
    val defaultEmbedded = Some(EmbeddedOptions.empty(defaultServerUrl))

    val (embedded, snippetId) =
      page match {
        case AnonymousResource(uuid) => {
          val snippetId = SnippetId(uuid, None)
          (None, snippetId)
        }

        case UserResource(login, uuid) => {
          val snippetId = SnippetId(uuid, Some(SnippetUserPart(login)))
          (None, snippetId)
        }

        case UserResourceUpdated(login, uuid, update) => {
          val snippetId = SnippetId(uuid, Some(SnippetUserPart(login, update)))
          (None, snippetId)
        }

        case EmbeddedAnonymousResource(uuid) => {
          val snippetId = SnippetId(uuid, None)
          (defaultEmbedded, snippetId)
        }

        case EmbeddedUserResource(login, uuid) => {
          val snippetId = SnippetId(uuid, Some(SnippetUserPart(login)))
          (defaultEmbedded, snippetId)
        }

        case EmbeddedUserResourceUpdated(login, uuid, update) => {
          val snippetId = SnippetId(uuid, Some(SnippetUserPart(login, update)))
          (defaultEmbedded, snippetId)
        }

      }

    Scastie(
      scastieId = UUID.randomUUID(),
      router = Some(router),
      snippetId = Some(snippetId),
      oldSnippetId = None,
      embedded = embedded,
      targetType = None,
      tryLibrary = None
    ).render
  }

  private def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()
}
