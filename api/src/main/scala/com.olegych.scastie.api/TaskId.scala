package com.olegych.scastie.api

import play.api.libs.json._

import play.api.libs.json.OFormat

object TaskId {
  implicit val formatSbtRunTaskId: OFormat[TaskId] =
    Json.format[TaskId]
}

case class TaskId(snippetId: SnippetId)
