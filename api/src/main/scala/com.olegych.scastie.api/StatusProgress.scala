package com.olegych.scastie.api

import play.api.libs.json._

object SbtRunnerState {
  implicit val formatSbtRunnerState: OFormat[SbtRunnerState] =
    Json.format[SbtRunnerState]
}

case class SbtRunnerState(
    config: Inputs,
    tasks: Vector[TaskId],
    sbtState: SbtState
) {
  def withoutTasks: SbtRunnerState = this.copy(tasks = Vector.empty)
}
sealed trait StatusProgress
object StatusProgress {
  implicit object StatusProgressFormat extends Format[StatusProgress] {
    private val formatSbt = Json.format[StatusProgress.Sbt]

    def writes(status: StatusProgress): JsValue = {

      status match {
        case StatusProgress.KeepAlive =>
          JsObject(Seq("tpe" -> JsString("StatusProgress.KeepAlive")))
        case runners: StatusProgress.Sbt =>
          formatSbt.writes(runners) ++ JsObject(Seq("tpe" -> JsString("StatusProgress.Sbt")))
      }
    }

    def reads(json: JsValue): JsResult[StatusProgress] = {
      json match {
        case obj: JsObject =>
          obj.value.get("tpe").orElse(obj.value.get("$type")) match {
            case Some(tpe) =>
              tpe match {
                case JsString("StatusProgress.KeepAlive") =>
                  JsSuccess(StatusProgress.KeepAlive)

                case JsString("StatusProgress.Sbt") =>
                  formatSbt.reads(json)

                case _ => JsError(Seq())
              }
            case None => JsError(Seq())
          }
        case _ => JsError(Seq())
      }
    }
  }

  case object KeepAlive extends StatusProgress
  case class Sbt(runners: Vector[SbtRunnerState]) extends StatusProgress
}
