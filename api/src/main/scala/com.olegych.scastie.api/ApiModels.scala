package com.olegych.scastie
package api

import java.util.UUID

sealed trait TaskId {
  def cost: Int
}

case class SbtRunTaskId(snippetId: SnippetId) extends TaskId {
  //([0s, 30s] upper bound Defined in SbtMain)
  def cost: Int = 15 // s
}

object EnsimeTaskId {
  def create = EnsimeTaskId(UUID.randomUUID())
}
case class EnsimeTaskId(id: UUID) extends TaskId {
  // ensime is pretty fast when indexed
  def cost: Int = 3 // s
}

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

  def url: String = {
    this match {
      case SnippetId(uuid, None) => uuid
      case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
        s"$login/$uuid/${update.getOrElse(0)}"
    }
  }

  def scalaJsUrl(end: String): String = {
    val middle = url
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

case class FormatRequest(
    code: String,
    worksheetMode: Boolean,
    targetType: ScalaTargetType
)
case class FormatResponse(
    formattedCode: Either[String, String]
)

sealed trait EnsimeRequest {
  def info: EnsimeRequestInfo
}
case class EnsimeRequestInfo(inputs: Inputs, offset: Int)
case class CompletionRequest(info: EnsimeRequestInfo) extends EnsimeRequest
case class TypeAtPointRequest(info: EnsimeRequestInfo) extends EnsimeRequest

sealed trait EnsimeResponse
case class CompletionResponse(completions: List[Completion])
    extends EnsimeResponse
case class TypeAtPointResponse(typeInfo: String) extends EnsimeResponse

case class EnsimeTaskRequest(request: EnsimeRequest, taskId: EnsimeTaskId)
case class EnsimeTaskResponse(response: EnsimeResponse, taskId: EnsimeTaskId)

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
    hint: String,
    typeInfo: String
)

case class TypeInfoAt(
    token: String,
    typeInfo: String
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal
