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

case class SbtRunnerConnect(hostname: String, port: Int)
case object SbtRunnerConnected

case class SnippetSummary(
    snippetId: SnippetId,
    summary: String,
    time: Long
)

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
case class UpdateEnsimeConfigRequest(info: EnsimeRequestInfo)
    extends EnsimeRequest

sealed trait EnsimeResponse
case class CompletionResponse(completions: List[Completion])
    extends EnsimeResponse
case class TypeAtPointResponse(typeInfo: String) extends EnsimeResponse

case class EnsimeTaskRequest(request: EnsimeRequest, taskId: EnsimeTaskId)
case class EnsimeTaskResponse(response: Option[EnsimeResponse],
                              taskId: EnsimeTaskId)

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
) {

  override def toString: String = target.renderSbt(this)
}

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
