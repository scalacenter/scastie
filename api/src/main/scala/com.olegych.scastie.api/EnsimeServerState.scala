package com.olegych.scastie.api

import play.api.libs.json._

sealed trait EnsimeServerState extends ServerState
object EnsimeServerState {
  case object Unknown extends EnsimeServerState {
    override def toString: String = "Unknown"
    def isReady: Boolean = false
  }

  case object Initializing extends EnsimeServerState {
    override def toString: String = "Initializing"
    def isReady: Boolean = false
  }

  case object CreatingConfig extends EnsimeServerState {
    override def toString: String = "CreatingConfig"
    def isReady: Boolean = false
  }

  case object Connecting extends EnsimeServerState {
    override def toString: String = "Connecting"
    def isReady: Boolean = false
  }

  case object Indexing extends EnsimeServerState {
    override def toString: String = "Indexing"
    def isReady: Boolean = false
  }

  case object Ready extends EnsimeServerState {
    override def toString: String = "Ready"
    def isReady: Boolean = true
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
        Indexing,
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