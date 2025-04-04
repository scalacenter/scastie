package com.olegych.scastie.api

import play.api.libs.json._
import play.api.libs.json.OFormat

sealed trait ConsoleOutput {
  def show: String
}

object ConsoleOutput {

  case class SbtOutput(output: ProcessOutput) extends ConsoleOutput {
    def show: String = s"sbt: ${output.line}"
  }

  case class UserOutput(output: ProcessOutput) extends ConsoleOutput {
    def show: String = output.line
  }

  case class ScastieOutput(line: String) extends ConsoleOutput {
    def show: String = s"scastie: $line"
  }

  implicit object ConsoleOutputFormat extends Format[ConsoleOutput] {
    val formatSbtOutput: OFormat[SbtOutput] = Json.format[SbtOutput]
    private val formatUserOutput            = Json.format[UserOutput]
    private val formatScastieOutput         = Json.format[ScastieOutput]

    def writes(output: ConsoleOutput): JsValue = {
      output match {
        case sbtOutput: SbtOutput => formatSbtOutput.writes(sbtOutput) ++ JsObject(Seq("tpe" -> JsString("SbtOutput")))
        case userOutput: UserOutput =>
          formatUserOutput.writes(userOutput) ++ JsObject(Seq("tpe" -> JsString("UserOutput")))
        case scastieOutput: ScastieOutput =>
          formatScastieOutput.writes(scastieOutput) ++ JsObject(Seq("tpe" -> JsString("ScastieOutput")))
      }
    }

    def reads(json: JsValue): JsResult[ConsoleOutput] = {
      json match {
        case obj: JsObject =>
          val vs = obj.value
          vs.get("tpe").orElse(vs.get("$type")) match {
            case Some(tpe) => tpe match {
                case JsString("SbtOutput")     => formatSbtOutput.reads(json)
                case JsString("UserOutput")    => formatUserOutput.reads(json)
                case JsString("ScastieOutput") => formatScastieOutput.reads(json)
                case _                         => JsError(Seq())
              }
            case None => JsError(Seq())
          }
        case _ => JsError(Seq())
      }
    }

  }

}
