package com.olegych.scastie.client

import play.api.libs.json._

sealed trait View
object View {
  case object Editor extends View
  case object BuildSettings extends View
  case object CodeSnippets extends View
  case object Status extends View

  implicit object ViewFormat extends Format[View] {
    def writes(view: View): JsValue = {
      JsString(view.toString)
    }

    private val values: Map[String, View] =
      List[View](
        Editor,
        BuildSettings,
        CodeSnippets,
        Status
      ).map(v => (v.toString, v)).toMap

    def reads(json: JsValue): JsResult[View] = {
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
