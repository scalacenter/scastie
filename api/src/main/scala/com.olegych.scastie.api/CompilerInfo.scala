package com.olegych.scastie.api

import play.api.libs.json._

object Severity {
  implicit object SeverityFormat extends Format[Severity] {
    def writes(severity: Severity): JsValue =
      severity match {
        case Info    => JsString("Info")
        case Warning => JsString("Warning")
        case Error   => JsString("Error")
      }

    def reads(json: JsValue): JsResult[Severity] = {
      json match {
        case JsString("Info")    => JsSuccess(Info)
        case JsString("Warning") => JsSuccess(Warning)
        case JsString("Error")   => JsSuccess(Error)
        case _                   => JsError(Seq())
      }
    }
  }
}

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

object Problem {
  implicit val formatProblem: OFormat[Problem] = Json.format[Problem]
}

case class Problem(
    severity: Severity,
    line: Option[Int],
    message: String
)
