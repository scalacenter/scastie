package com.olegych.scastie
package api

case object SbtPing
case object SbtPong

case class SnippetUserPart(login: String, update: Option[Int])
case class SnippetId(base64UUID: String, user: Option[SnippetUserPart]) {
  def isOwnedBy(user2: Option[User]): Boolean = {
    (user, user2) match {
      case (Some(SnippetUserPart(snippetLogin, _)),
            Some(User(userLogin, _, _))) =>
        snippetLogin == userLogin
      case _ => false
    }
  }

  def url(end: String): String = {
    val middle =
      this match {
        case SnippetId(uuid, None) => uuid
        case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
          s"$login/$uuid/${update.getOrElse(0)}"
      }

    s"/${Shared.scalaJsHttpPathPrefix}/$middle/$end"
  }
}

object User {
  // low tech solution
  val admins = Set(
    "dimart",
    "Duhemm",
    "heathermiller",
    "julienrf",
    "jvican",
    "MasseGuillaume",
    "olafurpg",
    "rorygraves",
    "travissarles"
  ) 
}
case class User(login: String, name: Option[String], avatar_url: String) {
  def isAdmin = User.admins.contains(login)  
}

case class SnippetSummary(snippetId: SnippetId, summary: String, time: Long)

case class FormatRequest(code: String,
                         worksheetMode: Boolean,
                         targetType: ScalaTargetType)
case class FormatResponse(formattedCode: Either[String, String])

case class CompletionRequest(inputs: Inputs, position: Int)
case class CompletionResponse(completions: List[Completion])

case class FetchResult(inputs: Inputs, progresses: List[SnippetProgress])

case class FetchScalaJs(snippetId: SnippetId)
case class FetchResultScalaJs(content: String)

case class FetchScalaJsSourceMap(snippetId: SnippetId)
case class FetchResultScalaJsSourceMap(content: String)

case class FetchScalaSource(snippetId: SnippetId)
case class FetchResultScalaSource(content: String)

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
)

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

case class Completion(
    hint: String
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal
