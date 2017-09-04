package com.olegych.scastie.api

import play.api.libs.json._

sealed trait EnsimeServerState
object EnsimeServerState {
  case object Unknown extends EnsimeServerState {
    override def toString: String = "Unknown"
  }
  case object Initializing extends EnsimeServerState {
    override def toString: String = "Initializing"
  }
  case object CreatingConfig extends EnsimeServerState {
    override def toString: String = "CreatingConfig"
  }
  case object Connecting extends EnsimeServerState {
    override def toString: String = "Connecting"
  }
  case object Ready extends EnsimeServerState {
    override def toString: String = "Ready"
  }

  implicit object EnsimeServerStateFormat extends Format[EnsimeServerState] {
    def writes(state: EnsimeServerState): JsValue = {
      JsString(state.toString)
    }

    private val values =
      List(
        Unknown,
        Initializing,
        CreatingConfig,
        Connecting,
        Ready
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[EnsimeServerState] = {
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