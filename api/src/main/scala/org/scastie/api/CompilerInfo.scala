package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

object Severity {
  implicit val severityEncoder: Encoder[Severity] = deriveEncoder[Severity]
  implicit val severityDecoder: Decoder[Severity] = deriveDecoder[Severity]
}

sealed trait Severity
case object Info    extends Severity
case object Warning extends Severity
case object Error   extends Severity

case class DiagnosticPosition(
  line: Int,
  character: Int
)

object DiagnosticPosition {
  implicit val positionEncoder: Encoder[DiagnosticPosition] = deriveEncoder[DiagnosticPosition]
  implicit val positionDecoder: Decoder[DiagnosticPosition] = deriveDecoder[DiagnosticPosition]
}

case class DiagnosticRange(
  start: DiagnosticPosition,
  end: DiagnosticPosition
)

object DiagnosticRange {
  implicit val rangeEncoder: Encoder[DiagnosticRange] = deriveEncoder[DiagnosticRange]
  implicit val rangeDecoder: Decoder[DiagnosticRange] = deriveDecoder[DiagnosticRange]
}

case class ScalaTextEdit(
  range: DiagnosticRange,
  newText: String
)

object ScalaTextEdit {
  implicit val scalaTextEditEncoder: Encoder[ScalaTextEdit] = deriveEncoder[ScalaTextEdit]
  implicit val scalaTextEditDecoder: Decoder[ScalaTextEdit] = deriveDecoder[ScalaTextEdit]
}

case class ScalaWorkspaceEdit(
  changes: List[ScalaTextEdit]
)

object ScalaWorkspaceEdit {
  implicit val scalaWorkspaceEditEncoder: Encoder[ScalaWorkspaceEdit] = deriveEncoder[ScalaWorkspaceEdit]
  implicit val scalaWorkspaceEditDecoder: Decoder[ScalaWorkspaceEdit] = deriveDecoder[ScalaWorkspaceEdit]
}

case class ScalaAction(
  title: String,
  description: Option[String],
  edit: Option[ScalaWorkspaceEdit]
)

object ScalaAction {
  implicit val scalaActionEncoder: Encoder[ScalaAction] = deriveEncoder[ScalaAction]
  implicit val scalaActionDecoder: Decoder[ScalaAction] = deriveDecoder[ScalaAction]
}

object Problem {
  implicit val problemEncoder: Encoder[Problem] = deriveEncoder[Problem]
  implicit val problemDecoder: Decoder[Problem] = deriveDecoder[Problem]
}

case class Problem(
  severity: Severity,
  startLine: Option[Int],
  endLine: Option[Int],
  startColumn: Option[Int],
  endColumn: Option[Int],
  message: String,
  actions: Option[List[ScalaAction]] = None
)
