package com.olegych.scastie.api

import play.api.libs.json._

sealed trait ConsoleOutput {
  def show: String
}

object ConsoleOutput {
  case class SbtOutput(line: String) extends ConsoleOutput {
    def show: String = s"sbt: $line"
  }

  case class UserOutput(line: String) extends ConsoleOutput {
    def show: String = line
  }

  case class ScastieOutput(line: String) extends ConsoleOutput {
    def show: String = s"scastie: $line"
  }

  implicit object ConsoleOutputFormat extends Format[ConsoleOutput] {
    val formatSbtOutput = Json.format[SbtOutput]
    private val formatUserOutput = Json.format[UserOutput]
    private val formatScastieOutput = Json.format[ScastieOutput]

    def writes(output: ConsoleOutput): JsValue = {
      output match {
        case sbtOutput: SbtOutput => {
          formatSbtOutput.writes(sbtOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("SbtOutput")))
        }

        case userOutput: UserOutput => {
          formatUserOutput.writes(userOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("UserOutput")))
        }

        case scastieOutput: ScastieOutput => {
          formatScastieOutput.writes(scastieOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("ScastieOutput")))
        }
      }
    }

    def reads(json: JsValue): JsResult[ConsoleOutput] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(tpe) => {
              tpe match {
                case JsString("SbtOutput")  => formatSbtOutput.reads(json)
                case JsString("UserOutput") => formatUserOutput.reads(json)
                case JsString("ScastieOutput") =>
                  formatScastieOutput.reads(json)
                case _ => JsError(Seq())
              }
            }
            case None => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }
}
