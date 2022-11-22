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


object EitherFormat {
  import play.api.libs.functional.syntax._
  implicit object JsEither {

    implicit def eitherReads[A, B](implicit A: Reads[A], B: Reads[B]): Reads[Either[A, B]] = {
      (JsPath \ "Left" \ "value").read[A].map(Left(_)) or
        (JsPath \ "Right" \ "value").read[B].map(Right(_))
    }

    implicit def eitherWrites[A, B](implicit A: Writes[A], B: Writes[B]): Writes[Either[A, B]] = Writes[Either[A, B]] {
      case Left(value)  => Json.obj("Left" -> Json.toJson(value))
      case Right(value) => Json.obj("Right" -> Json.toJson(value))
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

case class ScastieMetalsOptions(dependencies: Set[ScalaDependency], scalaTarget: ScalaTarget)

object ScastieMetalsOptions {
  implicit val scastieMetalsOptions: OFormat[ScastieMetalsOptions] = Json.format[ScastieMetalsOptions]
}

case class ScastieOffsetParams(content: String, offset: Int, isWorksheetMode: Boolean)

sealed trait FailureType {
  val msg: String
}

case class NoResult(msg: String) extends FailureType
case class PresentationCompilerFailure(msg: String) extends FailureType

object FailureType {
  implicit val failureTypeFormat: OFormat[FailureType] = Json.format[FailureType]
}

object NoResult {
  implicit val noResultFormat: OFormat[NoResult] = Json.format[NoResult]
}

object PresentationCompilerFailure {
  implicit val presentationCompilerFailureFormat: OFormat[PresentationCompilerFailure] = Json.format[PresentationCompilerFailure]
}

object ScastieOffsetParams {
  implicit val scastieOffsetParams: OFormat[ScastieOffsetParams] = Json.format[ScastieOffsetParams]
}

case class LSPRequestDTO(options: ScastieMetalsOptions, offsetParams: ScastieOffsetParams)
case class CompletionInfoRequest(options: ScastieMetalsOptions, completionItem: CompletionItemDTO)

object CompletionInfoRequest {
  implicit val completionInfoRequestFormat: OFormat[CompletionInfoRequest] = Json.format[CompletionInfoRequest]
}

case class InsertInstructions(text: String, cursorMove: Int)
case class AdditionalInsertInstructions(text: String, startLine: Int, startChar: Int, endLine: Int, endChar: Int)

case class CompletionItemDTO(
  label: String,
  detail: String,
  tpe: String,
  order: Option[Int],
  instructions: InsertInstructions,
  additionalInsertInstructions: List[AdditionalInsertInstructions],
  symbol: Option[String]
)


case class HoverDTO(from: Int, to: Int, content: String)

case class CompletionsDTO(items: Set[CompletionItemDTO])


object InsertInstructions {
  implicit val insertInstructionsFormat: OFormat[InsertInstructions] = Json.format[InsertInstructions]
}

object AdditionalInsertInstructions {
  implicit val additionalInsertInstructionsFormat: OFormat[AdditionalInsertInstructions] = Json.format[AdditionalInsertInstructions]
}

object CompletionItemDTO {
  implicit val completionItemDTOFormat: OFormat[CompletionItemDTO] = Json.format[CompletionItemDTO]
}

object CompletionsDTO {
  implicit val completionsDTOFormat: OFormat[CompletionsDTO] = Json.format[CompletionsDTO]
}

object LSPRequestDTO {
  implicit val lspRequestDTOFormat: OFormat[LSPRequestDTO] = Json.format[LSPRequestDTO]
}

object HoverDTO {
  implicit val hoverDTOFormat: OFormat[HoverDTO] = Json.format[HoverDTO]
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
