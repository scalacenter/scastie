package com.olegych.scastie.api

import play.api.libs.json._

case object SbtPing
case object SbtPong

case class SbtRunnerConnect(hostname: String, port: Int)
case object ActorConnected

object SnippetSummary {
  implicit val formatSnippetSummary: OFormat[SnippetSummary] =
    Json.format[SnippetSummary]
}

case class SnippetSummary(
    snippetId: SnippetId,
    summary: String,
    time: Long
)

object FormatRequest {
  implicit val formatFormatRequest: OFormat[FormatRequest] =
    Json.format[FormatRequest]
}

case class FormatRequest(
    code: String,
    isWorksheetMode: Boolean,
    scalaTarget: ScalaTarget
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

object FetchResult {
  implicit val formatFetchResult: OFormat[FetchResult] = Json.format[FetchResult]
  def create(inputs: Inputs, progresses: List[SnippetProgress]) = FetchResult(inputs, progresses.sortBy(p => (p.id, p.ts)))
}

case class FetchResult private (inputs: Inputs, progresses: List[SnippetProgress])

case class FetchScalaJs(snippetId: SnippetId)
case class FetchResultScalaJs(content: String)

case class FetchScalaJsSourceMap(snippetId: SnippetId)
case class FetchResultScalaJsSourceMap(content: String)

case class FetchScalaSource(snippetId: SnippetId)
case class FetchResultScalaSource(content: String)

object ScalaDependency {
  implicit val formatScalaDependency: OFormat[ScalaDependency] =
    Json.format[ScalaDependency]
}

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
) {
  def matches(sd: ScalaDependency): Boolean =
    sd.groupId == this.groupId &&
      sd.artifact == this.artifact
  
  override def toString: String = target.renderSbt(this)
}

object Project {
  implicit val formatProject: OFormat[Project] =
    Json.format[Project]
}

case class Project(
    organization: String,
    repository: String,
    logo: Option[String],
    artifacts: List[String]
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal
