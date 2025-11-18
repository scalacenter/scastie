package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

case object RunnerPing
case object RunnerPong

case class RunnerConnect(hostname: String, port: Int)
case object ActorConnected

object SnippetSummary {
  implicit val snippetSummaryEncoder: Encoder[SnippetSummary] = deriveEncoder[SnippetSummary]
  implicit val snippetSummaryDecoder: Decoder[SnippetSummary] = deriveDecoder[SnippetSummary]
}

case class SnippetSummary(
    snippetId: SnippetId,
    summary: String,
    time: Long
)

object FormatRequest {
  implicit val formatRequestEncoder: Encoder[FormatRequest] = deriveEncoder[FormatRequest]
  implicit val formatRequestDecoder: Decoder[FormatRequest] = deriveDecoder[FormatRequest]
}

case class FormatRequest(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget)

object FormatResponse {
  implicit val formatResponseEncoder: Encoder[FormatResponse] = deriveEncoder[FormatResponse]
  implicit val formatResponseDecoder: Decoder[FormatResponse] = deriveDecoder[FormatResponse]
}

case class FormatResponse(result: String)

object FetchResult {
  implicit val fetchResultEncoder: Encoder[FetchResult] = deriveEncoder[FetchResult]
  implicit val fetchResultDecoder: Decoder[FetchResult] = deriveDecoder[FetchResult]
  def create(inputs: BaseInputs, progresses: List[SnippetProgress]) = FetchResult(inputs, progresses.sortBy(p => (p.id, p.ts)))
}

case class FetchResult private (inputs: BaseInputs, progresses: List[SnippetProgress])

case class FetchScalaJs(snippetId: SnippetId)
case class FetchResultScalaJs(content: String)

case class FetchScalaJsSourceMap(snippetId: SnippetId)
case class FetchResultScalaJsSourceMap(content: String)

case class FetchScalaSource(snippetId: SnippetId)
case class FetchResultScalaSource(content: String)

object ScalaDependency {
  implicit val scalaDependencyEncoder: Encoder[ScalaDependency] = deriveEncoder[ScalaDependency]
  implicit val scalaDependencyDecoder: Decoder[ScalaDependency] = deriveDecoder[ScalaDependency]
}

case class ScalaDependency(groupId: String, artifact: String, target: ScalaTarget, version: String, isAutoResolve: Boolean = true) {
  def matches(sd: ScalaDependency): Boolean = sd.groupId == this.groupId && sd.artifact == this.artifact

  def renderSbt: String = {
    val crossSymbol = if (target.targetType == ScalaTargetType.JS) "%%%" else "%%"
    s"$groupId $crossSymbol $artifact % $version"
  }

  def renderScalaCli: String = {
    val resolveSymbol = if (isAutoResolve) "::" else ":"
    s"//> using dep $groupId$resolveSymbol$artifact:$version"
  }
}

case class ScastieMetalsOptions(dependencies: Set[ScalaDependency], scalaTarget: ScalaTarget)

object ScastieMetalsOptions {
  implicit val scastieMetalsOptionsEncoder: Encoder[ScastieMetalsOptions] = deriveEncoder[ScastieMetalsOptions]
  implicit val scastieMetalsOptionsDecoder: Decoder[ScastieMetalsOptions] = deriveDecoder[ScastieMetalsOptions]
}

case class ScastieOffsetParams(content: String, offset: Int, isWorksheetMode: Boolean)

sealed trait FailureType {
  val msg: String
}

case class NoResult(msg: String) extends FailureType
case class PresentationCompilerFailure(msg: String) extends FailureType
case class InvalidScalaVersion(msg: String) extends FailureType


object FailureType {
  implicit val failureTypeEncoder: Encoder[FailureType] = deriveEncoder[FailureType]
  implicit val noResultDecoder: Decoder[FailureType] = deriveDecoder[FailureType]
}

object NoResult {
  implicit val noResultEncoder: Encoder[NoResult] = deriveEncoder[NoResult]
  implicit val noResultDecoder: Decoder[NoResult] = deriveDecoder[NoResult]
}

object PresentationCompilerFailure {
  implicit val presentationCompilerFailureEncoder: Encoder[PresentationCompilerFailure] = deriveEncoder[PresentationCompilerFailure]
  implicit val presentationCompilerFailureDecoder: Decoder[PresentationCompilerFailure] = deriveDecoder[PresentationCompilerFailure]
}

object ScastieOffsetParams {
  implicit val scastieOffsetParamsEncoder: Encoder[ScastieOffsetParams] = deriveEncoder[ScastieOffsetParams]
  implicit val scastieOffsetParamsDecoder: Decoder[ScastieOffsetParams] = deriveDecoder[ScastieOffsetParams]
}

object InvalidScalaVersion {
  implicit val invalidScalaVersionEncoder: Encoder[InvalidScalaVersion] = deriveEncoder[InvalidScalaVersion]
  implicit val invalidScalaVersionDecoder: Decoder[InvalidScalaVersion] = deriveDecoder[InvalidScalaVersion]
}

case class LSPRequestDTO(options: ScastieMetalsOptions, offsetParams: ScastieOffsetParams)
case class CompletionInfoRequest(options: ScastieMetalsOptions, completionItem: CompletionItemDTO)

object CompletionInfoRequest {
  implicit val completionInfoRequestEncoder: Encoder[CompletionInfoRequest] = deriveEncoder[CompletionInfoRequest]
  implicit val completionInfoRequestDecoder: Decoder[CompletionInfoRequest] = deriveDecoder[CompletionInfoRequest]
}

case class InsertInstructions(text: String, editRange: EditRange)
case class AdditionalInsertInstructions(text: String, editRange: EditRange)
case class EditRange(startLine: Int, startChar: Int, endLine: Int, endChar: Int)

case class ScalaCompletionList(items: Set[CompletionItemDTO], isIncomplete: Boolean)

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

object EditRange {
  implicit val editRangeEncoder: Encoder[EditRange] = deriveEncoder[EditRange]
  implicit val editRangeDecoder: Decoder[EditRange] = deriveDecoder[EditRange]
}

object InsertInstructions {
  implicit val insertInstructionsEncoder: Encoder[InsertInstructions] = deriveEncoder[InsertInstructions]
  implicit val insertInstructionsDecoder: Decoder[InsertInstructions] = deriveDecoder[InsertInstructions]
}

object AdditionalInsertInstructions {
  implicit val additionalInsertInstructionsEncoder: Encoder[AdditionalInsertInstructions] = deriveEncoder[AdditionalInsertInstructions]
  implicit val additionalInsertInstructionsDecoder: Decoder[AdditionalInsertInstructions] = deriveDecoder[AdditionalInsertInstructions]
}

object ScalaCompletionList {
  implicit val scalaCompletionListEncoder: Encoder[ScalaCompletionList] = deriveEncoder[ScalaCompletionList]
  implicit val scalaCompletionListDecoder: Decoder[ScalaCompletionList] = deriveDecoder[ScalaCompletionList]
}

object CompletionItemDTO {
  implicit val completionItemDTOEncoder: Encoder[CompletionItemDTO] = deriveEncoder[CompletionItemDTO]
  implicit val completionItemDTODecoder: Decoder[CompletionItemDTO] = deriveDecoder[CompletionItemDTO]
}

object CompletionsDTO {
  implicit val completionsDTOEncoder: Encoder[CompletionsDTO] = deriveEncoder[CompletionsDTO]
  implicit val completionsDTODecoder: Decoder[CompletionsDTO] = deriveDecoder[CompletionsDTO]
}

object LSPRequestDTO {
  implicit val lspRequestDTOEncoder: Encoder[LSPRequestDTO] = deriveEncoder[LSPRequestDTO]
  implicit val lspRequestDTODecoder: Decoder[LSPRequestDTO] = deriveDecoder[LSPRequestDTO]
}

object HoverDTO {
  implicit val hoverDTOEncoder: Encoder[HoverDTO] = deriveEncoder[HoverDTO]
  implicit val hoverDTODecoder: Decoder[HoverDTO] = deriveDecoder[HoverDTO]
}

case class SignatureHelpDTO(
  signatures: Seq[SignatureInformationDTO],
  activeSignature: Int,
  activeParameter: Int
)

case class SignatureInformationDTO(
  label: String,
  documentation: String,
  parameters: Seq[ParameterInformationDTO]
)

case class ParameterInformationDTO(
  label: String,
  documentation: String
)

object SignatureHelpDTO {
  implicit val signatureHelpDTOEncoder: Encoder[SignatureHelpDTO] = deriveEncoder[SignatureHelpDTO]
  implicit val signatureHelpDTODecoder: Decoder[SignatureHelpDTO] = deriveDecoder[SignatureHelpDTO]
}

object SignatureInformationDTO {
  implicit val signatureInformationEncoder: Encoder[SignatureInformationDTO] = deriveEncoder[SignatureInformationDTO]
  implicit val signatureInformationDecoder: Decoder[SignatureInformationDTO] = deriveDecoder[SignatureInformationDTO]
}

object ParameterInformationDTO {
  implicit val parameterInformationEncoder: Encoder[ParameterInformationDTO] = deriveEncoder[ParameterInformationDTO]
  implicit val parameterInformationDecoder: Decoder[ParameterInformationDTO] = deriveDecoder[ParameterInformationDTO]
}

object Project {
  implicit val projectEncoder: Encoder[Project] = deriveEncoder[Project]
  implicit val projectDecoder: Decoder[Project] = deriveDecoder[Project]
}

case class Project(
    organization: String,
    repository: String,
    logo: Option[String],
    artifacts: List[String]
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal

sealed trait EditorMode
case object Default extends EditorMode
case object Vim     extends EditorMode
case object Emacs   extends EditorMode

object EditorMode {
  implicit val editorModeFormat: Encoder[EditorMode] = Encoder.encodeString.contramap {
    case Default => "Default"
    case Vim     => "Vim"
    case Emacs   => "Emacs"
  }
  implicit val editorModeDecoder: Decoder[EditorMode] = Decoder.decodeString.emap {
    case "Default" => Right(Default)
    case "Vim"     => Right(Vim)
    case "Emacs"   => Right(Emacs)
    case other     => Left(s"Unknown EditorMode: $other")
  }
}
