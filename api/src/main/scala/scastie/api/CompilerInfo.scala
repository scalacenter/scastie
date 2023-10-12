package scastie.api

import io.circe.generic.semiauto._
import io.circe._

object Severity {
  implicit val severityEncoder: Encoder[Severity] = deriveEncoder[Severity]
  implicit val severityDecoder: Decoder[Severity] = deriveDecoder[Severity]
  // implicit object SeverityFormat extends Format[Severity] {
  //   def writes(severity: Severity): JsValue =
  //     severity match {
  //       case Info    => JsString("Info")
  //       case Warning => JsString("Warning")
  //       case Error   => JsString("Error")
  //     }

  //   def reads(json: JsValue): JsResult[Severity] = {
  //     json match {
  //       case JsString("Info")    => JsSuccess(Info)
  //       case JsString("Warning") => JsSuccess(Warning)
  //       case JsString("Error")   => JsSuccess(Error)
  //       case _                   => JsError(Seq())
  //     }
  //   }
  // }
}

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

object Problem {
  implicit val problemEncoder: Encoder[Problem] = deriveEncoder[Problem]
  implicit val problemDecoder: Decoder[Problem] = deriveDecoder[Problem]
}

case class Problem(
    severity: Severity,
    line: Option[Int],
    message: String
)
