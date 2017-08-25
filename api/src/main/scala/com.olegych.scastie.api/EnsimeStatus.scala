package com.olegych.scastie.api

import play.api.libs.json._

sealed trait EnsimeStatus
case object EnsimeDown extends EnsimeStatus
case object EnsimeRestarting extends EnsimeStatus
case object EnsimeUp extends EnsimeStatus

object EnsimeStatus {
  implicit object EnsimeStatusFormat extends Format[EnsimeStatus] {
    def writes(status: EnsimeStatus): JsValue = {
      JsString(status.toString)
    }

    private val values =
      List(
        EnsimeDown,
        EnsimeRestarting,
        EnsimeUp
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[EnsimeStatus] = {
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