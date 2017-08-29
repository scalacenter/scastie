package com.olegych.scastie.api

import java.util.UUID
import play.api.libs.json._

import scala.concurrent.duration._

trait TaskId

object SbtRunTaskId {
  implicit val formatSbtRunTaskId = Json.format[SbtRunTaskId]
}

case class SbtRunTaskId(snippetId: SnippetId) extends TaskId

object EnsimeTaskId {
  def create = EnsimeTaskId(UUID.randomUUID())
  implicit val formatEnsimeTaskId = Json.format[EnsimeTaskId]
}
case class EnsimeTaskId(id: UUID) extends TaskId
