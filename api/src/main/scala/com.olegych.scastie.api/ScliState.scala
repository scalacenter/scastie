package com.olegych.scastie.api

import play.api.libs.json._

sealed trait ScliState extends ServerState
object ScliState {
  case object Unknown extends ScliState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends ScliState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit object ScliStateFormat extends Format[ScliState] {
    def writes(state: ScliState): JsValue = {
      JsString(state.toString)
    }

    private val values =
      List(
        Unknown,
        Disconnected
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[ScliState] = {
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