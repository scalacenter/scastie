package com.olegych.scastie.api

import play.api.libs.json._

case object SbtPing
case object SbtPong

case class SbtRunnerConnect(hostname: String, port: Int)
case object SbtRunnerConnected

object SnippetSummary {
  implicit val formatSnippetSummary
    : OFormat[com.olegych.scastie.api.SnippetSummary] =
    Json.format[SnippetSummary]
}

case class SnippetSummary(
    snippetId: SnippetId,
    summary: String,
    time: Long
)

object FormatRequest {
  implicit val formatFormatRequest
    : OFormat[com.olegych.scastie.api.FormatRequest] =
    Json.format[FormatRequest]
}

case class FormatRequest(
    code: String,
    worksheetMode: Boolean,
    targetType: ScalaTargetType
)

object FormatResponse {
  implicit object FormatResponseFormat extends Format[FormatResponse] {
    def writes(response: FormatResponse): JsValue = {
      response.result match {
        case Left(error) =>
          JsObject(
            Seq(
              "Left" -> JsString(error)
            )
          )
        case Right(formatedCode) =>
          JsObject(
            Seq(
              "Right" -> JsString(formatedCode)
            )
          )
      }
    }

    def reads(json: JsValue): JsResult[FormatResponse] = {
      json match {
        case JsObject(v) =>
          v.toList match {
            case List(("Left", JsString(error))) =>
              JsSuccess(FormatResponse(Left(error)))

            case List(("Right", JsString(formatedCode))) =>
              JsSuccess(FormatResponse(Right(formatedCode)))

            case _ =>
              JsError(Seq())
          }

        case _ =>
          JsError(Seq())
      }
    }
  }
}

case class FormatResponse(
    result: Either[String, String]
)

sealed trait EnsimeRequest {
  def inputs: Inputs
}

object EnsimeRequestInfo {
  implicit val formatEnsimeRequestInfo
    : OFormat[com.olegych.scastie.api.EnsimeRequestInfo] =
    Json.format[EnsimeRequestInfo]
}

case class EnsimeRequestInfo(inputs: Inputs, offset: Int)

object AutoCompletionRequest {
  implicit val formatAutoCompletionRequest
    : OFormat[com.olegych.scastie.api.AutoCompletionRequest] =
    Json.format[AutoCompletionRequest]
}

case class AutoCompletionRequest(info: EnsimeRequestInfo)
    extends EnsimeRequest {
  def inputs: Inputs = info.inputs
}

object TypeAtPointRequest {
  implicit val formatTypeAtPointRequest
    : OFormat[com.olegych.scastie.api.TypeAtPointRequest] =
    Json.format[TypeAtPointRequest]
}

case class TypeAtPointRequest(info: EnsimeRequestInfo) extends EnsimeRequest {
  def inputs: Inputs = info.inputs
}

object UpdateEnsimeConfigRequest {
  implicit val formatUpdateEnsimeConfigRequest
    : OFormat[com.olegych.scastie.api.UpdateEnsimeConfigRequest] =
    Json.format[UpdateEnsimeConfigRequest]
}

case class UpdateEnsimeConfigRequest(newInputs: Inputs) extends EnsimeRequest {
  def inputs: Inputs = newInputs
}

object Completion {
  implicit val formatCompletion: OFormat[com.olegych.scastie.api.Completion] =
    Json.format[Completion]
}

case class Completion(
    hint: String,
    signature: String,
    resultType: String
)

object TypeInfoAt {
  implicit val formatTypeInfoAt: OFormat[com.olegych.scastie.api.TypeInfoAt] =
    Json.format[TypeInfoAt]
}

case class TypeInfoAt(
    token: String,
    typeInfo: String
)

sealed trait EnsimeResponse

object AutoCompletionResponse {
  implicit val formatAutoCompletionResponse
    : OFormat[com.olegych.scastie.api.AutoCompletionResponse] =
    Json.format[AutoCompletionResponse]
}

case class AutoCompletionResponse(completions: List[Completion])
    extends EnsimeResponse

object TypeAtPointResponse {
  implicit val formatTypeAtPointResponse
    : OFormat[com.olegych.scastie.api.TypeAtPointResponse] =
    Json.format[TypeAtPointResponse]
}

case class TypeAtPointResponse(typeInfo: String) extends EnsimeResponse

object EnsimeConfigUpdated {
  implicit object EnsimeConfigUpdatedFormat
      extends Format[EnsimeConfigUpdated] {
    def writes(response: EnsimeConfigUpdated): JsValue = {
      JsString("EnsimeConfigUpdated")
    }

    def reads(json: JsValue): JsResult[EnsimeConfigUpdated] = {
      json match {
        case JsString("EnsimeConfigUpdated") => JsSuccess(EnsimeConfigUpdated())
        case _                               => JsError(Seq())
      }
    }
  }
}

case class EnsimeConfigUpdated() extends EnsimeResponse

case class EnsimeTaskRequest(request: EnsimeRequest, taskId: EnsimeTaskId)
case class EnsimeTaskResponse(response: Option[EnsimeResponse],
                              taskId: EnsimeTaskId)

object FetchResult {
  implicit val formatFetchResult: OFormat[com.olegych.scastie.api.FetchResult] =
    Json.format[FetchResult]
}

case class FetchResult(inputs: Inputs, progresses: List[SnippetProgress])

case class FetchScalaJs(snippetId: SnippetId)
case class FetchResultScalaJs(content: String)

case class FetchScalaJsSourceMap(snippetId: SnippetId)
case class FetchResultScalaJsSourceMap(content: String)

case class FetchScalaSource(snippetId: SnippetId)
case class FetchResultScalaSource(content: String)

object ScalaDependency {
  implicit val formatScalaDependency
    : OFormat[com.olegych.scastie.api.ScalaDependency] =
    Json.format[ScalaDependency]
}

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
) {

  override def toString: String = target.renderSbt(this)
}

object Project {
  implicit val formatProject: OFormat[com.olegych.scastie.api.Project] =
    Json.format[Project]
}

case class Project(
    organization: String,
    repository: String,
    logo: Option[List[String]] = None,
    artifacts: List[String] = Nil
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal
