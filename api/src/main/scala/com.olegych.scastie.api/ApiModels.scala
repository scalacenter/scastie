package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeResponse.CompletionItem

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

sealed trait EnsimeRequest {
  def info: EnsimeRequestInfo
}

case class EnsimeRequestInfo(inputs: Inputs, offset: Int)
case class CompletionRequest(info: EnsimeRequestInfo) extends EnsimeRequest
case class TypeAtPointRequest(info: EnsimeRequestInfo) extends EnsimeRequest

sealed trait EnsimeResponse
case class CompletionResponse(
    completions: List[CompletionItem]
) extends EnsimeResponse

case class TypeAtPointResponse(
    typeInfo: String
) extends EnsimeResponse

case class EnsimeTaskRequest(
    request: EnsimeRequest,
    taskId: EnsimeTaskId
)

case class EnsimeTaskResponse(
    response: Option[EnsimeResponse],
    taskId: EnsimeTaskId
)
