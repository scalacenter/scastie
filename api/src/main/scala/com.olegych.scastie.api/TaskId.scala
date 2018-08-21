package com.olegych.scastie.api

import play.api.libs.json._

import play.api.libs.json.OFormat

trait TaskId

object SbtRunTaskId {
  implicit val formatSbtRunTaskId: OFormat[SbtRunTaskId] =
    Json.format[SbtRunTaskId]
}

case class SbtRunTaskId(snippetId: SnippetId) extends TaskId
