package scastie.endpoints

import sttp.tapir._
import com.olegych.scastie.api._
import sttp.model.Header
import sttp.model.MediaType


object SnippetMatcher {
  import SnippetIdUtils._

  def getApiSnippetEndpoints[SecurityInput, Output](baseEndpoint: Endpoint[SecurityInput, Unit, Output, Unit, Any]): List[Endpoint[SecurityInput, SnippetId, Output, Unit, Any]] = {
    val userLatestSnippetEndpoint = baseEndpoint.get.in(path[String]("user") / path[String]("snippetId"))
        .mapIn(SnippetIdUtils.toSnippetId(_))(snippetId => (snippetId.user.fold("")(_.login), snippetId.base64UUID))

    val userSnippetEndpoint = baseEndpoint.get.in(path[String]("user") / path[String]("snippetId") / path[Int]("rev"))
        .mapIn(SnippetIdUtils.toSnippetId(_))(snippetId => (snippetId.user.fold("")(_.login), snippetId.base64UUID, snippetId.user.fold(0)(_.update)))

    val snippetEndpoint = baseEndpoint.get.in(path[String]("snippetId"))
        .mapIn(SnippetIdUtils.toSnippetId(_))(snippetId => (snippetId.base64UUID))

    userLatestSnippetEndpoint :: userSnippetEndpoint :: snippetEndpoint :: Nil
  }

  def getFrontPageSnippetEndpoints(baseEndpoint: PublicEndpoint[Unit, Unit, Unit, Any]): List[PublicEndpoint[MaybeEmbeddedSnippet, Unit, FrontPageSnippet, Any]] = {
    def addOptionalJSPath(path: String) = s"$path[.js]"
    val snippetOutputVariant = oneOf[FrontPageSnippet](
        oneOfVariant[EmbeddedSnippet](stringBody.map(EmbeddedSnippet(_))(_.content) and header(Header.contentType(MediaType.TextJavascript))),
        oneOfVariant[UniversalSnippet](htmlBodyUtf8.map(UniversalSnippet(_))(_.content))
      )

    val userLatestSnippetEndpoint = baseEndpoint.get.in(path[String]("user") / path[String](addOptionalJSPath("snippetId")))
        .mapInDecode(SnippetIdUtils.toMaybeSnippetId(_))(snippetId => (snippetId.user, snippetId.snippetId))
        .out(snippetOutputVariant)

    val userSnippetEndpoint = baseEndpoint.get.in(path[String]("user") / path[String]("snippetId") / path[String](addOptionalJSPath("rev")))
        .mapInDecode(SnippetIdUtils.toMaybeSnippetId(_))(snippetId => (snippetId.user, snippetId.snippetId, snippetId.rev))
        .out(snippetOutputVariant)

    val snippetEndpoint = baseEndpoint.get.in(path[String](addOptionalJSPath("snippetId")))
        .mapInDecode(SnippetIdUtils.toMaybeSnippetId(_))(snippetId => (snippetId.snippetId))
        .out(snippetOutputVariant)

    userLatestSnippetEndpoint :: userSnippetEndpoint :: snippetEndpoint :: Nil
  }
}
