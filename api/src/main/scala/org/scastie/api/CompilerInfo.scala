package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

object Severity {
  implicit val severityEncoder: Encoder[Severity] = deriveEncoder[Severity]
  implicit val severityDecoder: Decoder[Severity] = deriveDecoder[Severity]
}

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

object Problem {
  implicit val problemEncoder: Encoder[Problem] = deriveEncoder[Problem]
  implicit val problemDecoder: Decoder[Problem] = deriveDecoder[Problem]
}

case class Problem(severity: Severity, line: Option[Int], startColumn: Option[Int], endColumn: Option[Int], message: String)
