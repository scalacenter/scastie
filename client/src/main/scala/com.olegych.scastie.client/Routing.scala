package com.olegych.scastie.client

import com.olegych.scastie.api.{Inputs, Project, ScalaDependency, ScalaTarget, ScalaTargetType, ScalaVersions, SnippetId, SnippetUserPart}
import com.olegych.scastie.client.components._
import japgolly.scalajs.react._
import vdom.all._
import extra.router._
import play.api.libs.json.Json

import java.util.UUID

class Routing(defaultServerUrl: String) {
  val config: RouterConfig[Page] = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    val embedded = "embedded"
    val alpha = string("[a-zA-Z0-9-]+")
    val snippetId = string("[a-zA-Z0-9-]{22}")
    val targetType = queryToMap.pmap { map =>
      (
        map.get("target"),
        map.get("c"),
      ) match {
        case (Some(target), c) =>
          ScalaTargetType.parse(target.toUpperCase).map(target => TargetTypePage(target, c))
        case _ => None
      }
    }(p => Map("target" -> p.targetType.toString) ++ p.code.map("c" -> _))

    val inputs = queryToMap.pmap { map =>
      map.get("inputs").flatMap { inputs =>
        Json
          .fromJson[Inputs](Json.parse(inputs))
          .fold({ e =>
            println(s"failed to parse ${inputs}")
            println(e)
            None
          }, inputs => Some(InputsPage(inputs)))
      }
    }(p => Map("inputs" -> Json.toJson(p.inputs).toString().replace("{", "%7B").replace("}", "%7D")))

    def parseTryLibrary(map: Map[String, String]) = {
      (
        map.get("g"),
        map.get("a"),
        map.get("v"),
        map.get("o"),
        map.get("r"),
        map.get("c"),
      ) match {
        case (Some(g), Some(a), Some(v), o, r, c) =>
          val target = map.get("t").flatMap(ScalaTargetType.parse) match {
            case Some(t @ ScalaTargetType.Scala2) =>
              map.get("sv").map(sv => ScalaTarget.Jvm(ScalaVersions.find(t, sv)))
            case Some(t @ ScalaTargetType.JS) =>
              (map.get("sv"), map.get("sjsv")) match {
                case (Some(sv), sjsv) =>
                  Some(ScalaTarget.Js(ScalaVersions.find(t, sv), sjsv.getOrElse(ScalaTarget.Js.default.scalaJsVersion)))
                case _ => None
              }
            case _ => None
          }
          val project = (r, o) match {
            case (Some(r), Some(o)) => Project(organization = o, repository = r, None, Nil)
            case _                  => Project("", "", None, Nil)
          }
          target.map(t => TryLibraryPage(ScalaDependency(g, a, t, v), project, c))
        case _ => None
      }
    }

    def renderTryLibrary(dep: TryLibraryPage) = {
      val tm = dep.dependency.target match {
        case ScalaTarget.Jvm(sv)      => Map("sv" -> sv)
        case ScalaTarget.Js(sv, sjsv) => Map("sv" -> sv, "sjsv" -> sjsv)
        case _                        => Map[String, String]()
      }
      tm ++ dep.code.map("c" -> _) ++ Map(
        "g" -> dep.dependency.groupId,
        "a" -> dep.dependency.artifact,
        "v" -> dep.dependency.version,
        "r" -> dep.project.repository,
        "o" -> dep.project.organization,
      )
    }

    val tryLibrary = queryToMap.pmap(parseTryLibrary)(renderTryLibrary)

    val anon = snippetId
    val user = alpha / snippetId
    val userUpdate = alpha / snippetId / int
    val oldId = int

    (
      trimSlashes
        | staticRoute(root, Home) ~>
          renderR(renderScastieDefault)
        | dynamicRouteCT("try" ~ tryLibrary) ~>
          dynRenderR((page, router) => renderTryLibraryPage(page, router))
        | dynamicRouteCT(inputs) ~>
          dynRenderR((page, router) => renderInputs(page, router))
        | dynamicRouteCT(targetType) ~>
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
    ).notFound(redirectToPage(Home)(SetRouteVia.HistoryReplace))
      .renderWith((page, router) => layout(page, router))
  }

  private def renderScastieDefault(router: RouterCtl[Page]): VdomElement = {

    Scastie.default(router).render
  }

  private def renderTargetTypePage(page: TargetTypePage, router: RouterCtl[Page]): VdomElement = {
    Scastie.default(router).copy(targetType = Some(page.targetType), code = page.code).render
  }

  private def renderTryLibraryPage(page: TryLibraryPage, router: RouterCtl[Page]): VdomElement = {
    Scastie.default(router).copy(tryLibrary = Some((page.dependency, page.project)), code = page.code).render
  }

  private def renderInputs(page: InputsPage, router: RouterCtl[Page]): VdomElement = {
    Scastie.default(router).copy(inputs = Some(page.inputs)).render
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

    val (embedded, snippetId) = page match {
      case AnonymousResource(uuid) =>
        val snippetId = SnippetId(uuid, None)
        (None, snippetId)
      case UserResource(login, uuid) =>
        val snippetId = SnippetId(uuid, Some(SnippetUserPart(login)))
        (None, snippetId)
      case UserResourceUpdated(login, uuid, update) =>
        val snippetId = SnippetId(uuid, Some(SnippetUserPart(login, update)))
        (None, snippetId)
      case EmbeddedAnonymousResource(uuid) =>
        val snippetId = SnippetId(uuid, None)
        (defaultEmbedded, snippetId)
      case EmbeddedUserResource(login, uuid) =>
        val snippetId = SnippetId(uuid, Some(SnippetUserPart(login)))
        (defaultEmbedded, snippetId)
      case EmbeddedUserResourceUpdated(login, uuid, update) =>
        val snippetId = SnippetId(uuid, Some(SnippetUserPart(login, update)))
        (defaultEmbedded, snippetId)
    }

    Scastie(
      scastieId = UUID.randomUUID(),
      router = Some(router),
      snippetId = Some(snippetId),
      oldSnippetId = None,
      embedded = embedded,
      targetType = None,
      tryLibrary = None,
      code = None,
      inputs = None,
    ).render
  }

  private def layout(c: RouterCtl[Page], r: Resolution[Page]) = r.render()
}
