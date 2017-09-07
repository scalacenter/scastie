package com.olegych.scastie.api

import java.util.UUID
import play.api.libs.json._

import play.api.libs.json.OFormat

trait TaskId

object SbtRunTaskId {
  implicit val formatSbtRunTaskId: OFormat[SbtRunTaskId] =
    Json.format[SbtRunTaskId]
}

case class SbtRunTaskId(snippetId: SnippetId) extends TaskId

object EnsimeTaskId {
  def create: EnsimeTaskId = EnsimeTaskId(UUID.randomUUID())
  implicit val formatEnsimeTaskId: OFormat[EnsimeTaskId] =
    Json.format[EnsimeTaskId]
}
case class EnsimeTaskId(id: UUID) extends TaskId
