package com.olegych.scastie.api

import play.api.libs.json._

trait ProcessOutputType
object ProcessOutputType {
  case object StdOut extends ProcessOutputType
  case object StdErr extends ProcessOutputType

  implicit object ProcessOutputTypeFormat extends Format[ProcessOutputType] {
    def writes(processOutputType: ProcessOutputType): JsValue = {
      JsString(processOutputType.toString)
    }

    private val values =
      List(
        StdOut,
        StdErr
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[ProcessOutputType] = {
      json match {
        case JsString(tpe) => {
          values.get(tpe) match {
            case Some(v) => JsSuccess(v)
            case _       => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }
}

object ProcessOutput {
  implicit val formatProcessOutput: OFormat[ProcessOutput] =
    Json.format[ProcessOutput]
}

case class ProcessOutput(
    line: String,
    tpe: ProcessOutputType
)
