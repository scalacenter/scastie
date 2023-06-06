package scastie.endpoints

import sttp.tapir._
import sttp.tapir.files._

import play.api.libs.json._
import sttp.model.headers.CacheDirective
import sttp.model.Header
import sttp.model.MediaType
import scastie.endpoints.SnippetIdUtils._

object FrontPageEndpoints {
  val classLoader = ClassLoader.getSystemClassLoader()

  val embeddedJSEndpoint = staticResourcesGetEndpoint("embedded.js")
    .out(
      header(Header.cacheControl(CacheDirective.NoCache)) and
      header(Header.contentType(MediaType.TextJavascript))
    )
    .description("Access point to JavaScript source required to run embedded snippets")

  val embeddedCSSEndpoint= staticResourcesGetEndpoint("public" / "embedded.css")
    .out(
      header(Header.cacheControl(CacheDirective.NoCache)) and
      header(Header.contentType(MediaType.TextCss))
    )
    .description("Access point to CSS source required to run embedded snippets")

  val publicAssetsEndpoint = staticResourcesGetEndpoint("public")

  val indexEndpoint = staticResourcesGetEndpoint("")
    .out(header(Header.cacheControl(CacheDirective.NoCache)))

  object ColorScheme extends Enumeration {
    type ColorScheme = Value
    val Light = Value("light")
    val Dark = Value("dark")
  }

  implicit val colorSchemeFormat: Format[ColorScheme.Value] = Json.formatEnum(ColorScheme)

  val frontPageSnippetEndpoints: List[PublicEndpoint[(MaybeEmbeddedSnippet, Option[ColorScheme.Value]), Unit, FrontPageSnippet, Any]] =
    SnippetMatcher.getFrontPageSnippetEndpoints(endpoint).map { snippetEndpoint =>
      snippetEndpoint.in(query[Option[ColorScheme.Value]]("theme"))
        .description(
          """|This endpoint serves 3 purposes:
             | - it is used to access and share snippets between users,
             | - it is used to access open graph metadata for a snippet.
             | - it is used to access embedded snippet JavaScript code,
             |
             |Snippet access and open graph metadata is determined by the snippet Id.
             |The snippet Id consists of 3 parts: a user name, a snippet id and optionally a revision number.
             |The user name is optional and if not provided, the snippet is assumed to be owned by the anonymous user.
             |Revision number is also optional is always bounded to username. If not provided, the latest revision is used.
             |Example:
             |  - https://scastie.scala-lang.org/randomUUID
             |  - https://scastie.scala-lang.org/username/randomUUID
             |  - https://scastie.scala-lang.org/username/randomUUID/1
             |
             |Embedded snippet access is determined in the same way as snippet access,
             |but additionally requires a `.js` suffix. It also takes optional `theme` parameter.
             |Example:
             |  - https://scastie.scala-lang.org/randomUUID.js
             |  - https://scastie.scala-lang.org/username/randomUUID.js
             |  - https://scastie.scala-lang.org/username/randomUUID/1.js
             |
             |""".stripMargin)
    }

  val endpoints: List[AnyEndpoint] = List(
    embeddedJSEndpoint,
    embeddedCSSEndpoint,
    publicAssetsEndpoint,
    indexEndpoint,
  ) ::: frontPageSnippetEndpoints
}
