package com.olegych.scastie.api

import play.api.libs.json._

import scala.collection.immutable.Queue

object Runner {
  implicit val formatRunner: play.api.libs.json.OFormat[com.olegych.scastie.api.Runner] = Json.format[Runner]
}

case class Runner(tasks: Queue[TaskId])

object StatusProgress {
  implicit object StatusProgressFormat extends Format[StatusProgress] {
    private val formatStatusInfo = Json.format[StatusInfo]

    def writes(status: StatusProgress): JsValue = {

      status match {
        case StatusKeepAlive => {
          JsObject(Seq("$type" -> JsString("StatusKeepAlive")))
        }

        case si: StatusInfo => {
          formatStatusInfo.writes(si).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("StatusInfo")))
        }
      }
    }

    def reads(json: JsValue): JsResult[StatusProgress] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(tpe) => {
              tpe match {
                case JsString("StatusKeepAlive") => JsSuccess(StatusKeepAlive)
                case JsString("StatusInfo")      => formatStatusInfo.reads(json)
                case _                           => JsError(Seq())
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

sealed trait StatusProgress
case object StatusKeepAlive extends StatusProgress
case class StatusInfo(runners: Vector[Runner]) extends StatusProgress
