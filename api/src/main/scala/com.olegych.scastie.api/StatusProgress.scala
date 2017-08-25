package com.olegych.scastie.api

import play.api.libs.json._

import scala.collection.immutable.Queue

object Runner {
  implicit val formatRunner: OFormat[Runner] =
    Json.format[Runner]
}

case class Runner(tasks: Queue[TaskId])

object StatusProgress {
  implicit object StatusProgressFormat extends Format[StatusProgress] {
    private val formatRunnersInfo = Json.format[StatusRunnersInfo]
    private val formatEnsimeInfo = Json.format[StatusEnsimeInfo]

    def writes(status: StatusProgress): JsValue = {

      status match {
        case StatusKeepAlive => {
          JsObject(Seq("$type" -> JsString("StatusKeepAlive")))
        }

        case runners: StatusRunnersInfo => {
          formatRunnersInfo.writes(runners).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("StatusRunnersInfo")))
        }

        case ensime: StatusEnsimeInfo => {
          formatEnsimeInfo.writes(ensime).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("StatusEnsimeInfo")))
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
                case JsString("StatusRunnersInfo") =>
                  formatRunnersInfo.reads(json)
                case JsString("StatusEnsimeInfo") =>
                  formatEnsimeInfo.reads(json)
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

sealed trait StatusProgress
case object StatusKeepAlive extends StatusProgress
case class StatusRunnersInfo(runners: Vector[Runner]) extends StatusProgress
case class StatusEnsimeInfo(ensimeStatus: EnsimeStatus) extends StatusProgress
