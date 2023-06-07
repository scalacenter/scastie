package scastie.endpoints

import sttp.tapir._
import sttp.model.StatusCode
import sttp.tapir.json.play._
import sttp.tapir.generic.auto._

import com.olegych.scastie.api._

object ApiEndpoints {
  import OAuthEndpoints._

  val publicEndpoint = endpoint.in("api")
  val baseEndpoint = optionalSecureEndpoint.in("api")
  val secureApiEndpoint = secureEndpoint.in("api")

  val runEndpoint: PublicEndpoint[Inputs, Unit, SnippetId, Any] =
    publicEndpoint.post.in("run").in(jsonBody[Inputs]).out(jsonBody[SnippetId])
      .description(
        """|Endpoint used to run snippet without saving it to the database.
           |This is the recommended way to run snippets from 3rd party websites.
           |""".stripMargin
      )
      .name("Run snippet")

  val formatEndpoint: PublicEndpoint[FormatRequest, Unit, FormatResponse, Any]  =
    publicEndpoint.post.in("format").in(jsonBody[FormatRequest]).out(jsonBody[FormatResponse])
      .name("Format snippet")

  val saveEndpoint: Endpoint[OptionalUserSession, Inputs, String, SnippetId, Any] =
    baseEndpoint.post.in("save").in(jsonBody[Inputs]).out(jsonBody[SnippetId])

  val forkEndpoint: Endpoint[OptionalUserSession, EditInputs, String, SnippetId, Any]  =
    baseEndpoint.post.in("fork").in(jsonBody[EditInputs]).out(jsonBody[SnippetId])
      .description(
        """|Endpoint used to run and then save snippet with a new unique UUID.
           |Should not be used from 3rd party websites, and all custom integrations
           |should use `run` endpoint instead.
           |""".stripMargin
      )

  // To be changed to `DELETE` method after we migrate to STTP client
  val deleteEndpoint: Endpoint[UserSession, SnippetId, String, Boolean, Any]  =
    secureApiEndpoint.delete.in("delete").in(jsonBody[SnippetId]).out(jsonBody[Boolean])
  val updateEndpoint: Endpoint[UserSession, EditInputs, String, SnippetId, Any]  =
    secureApiEndpoint.post.in("update").in(jsonBody[EditInputs]).out(jsonBody[SnippetId])
      .description(
        """|Endpoint used to run and then update the snippet revision number.
           |Should not be used from 3rd party websites, and all custom integrations
           |should use `run` endpoint instead.
           |""".stripMargin
        )

  val userSettingsEndpoint: Endpoint[UserSession, Unit, String, User, Any] =
    secureApiEndpoint.get.in("user" / "settings").out(jsonBody[User])
  val userSnippetsEndpoint: Endpoint[UserSession, Unit, String, List[SnippetSummary], Any] =
    secureApiEndpoint.get.in("user" / "snippets").out(jsonBody[List[SnippetSummary]])

  private val snippetBaseEndpoint = publicEndpoint.in("snippets")
  val snippetApiEndpoints = SnippetMatcher.getApiSnippetEndpoints(snippetBaseEndpoint, "Get ").map { endpoint =>
    endpoint
      .errorOut(statusCode(StatusCode.NotFound))
      .out(jsonBody[FetchResult])
      .description(
        """|Endpoint used to fetch snippet by its UUID.
           |
           |Snippet access is determined by the snippet Id.
           |The snippet Id consists of 3 parts: a user name, a snippet id and optionally a revision number.
           |The user name is optional and if not provided, the snippet is assumed to be owned by the anonymous user.
           |Revision number is also optional is always bounded to username. If not provided, the latest revision is used.
           |
           |This is the actual endpoint that fetches the content of the saved snippet.
           |The fetched snippet is self contained and doesn't need to be run to display output.
           |""".stripMargin
        )
  }

  val publicEndpoints: List[AnyEndpoint] = List(
    runEndpoint,
    formatEndpoint,
  ) ::: snippetApiEndpoints

  val internalEndpoints: List[AnyEndpoint] = List(
    saveEndpoint,
    forkEndpoint,
    deleteEndpoint,
    updateEndpoint,
    userSettingsEndpoint,
    userSnippetsEndpoint,
  )

  val endpoints: List[AnyEndpoint] = publicEndpoints ::: internalEndpoints
}

