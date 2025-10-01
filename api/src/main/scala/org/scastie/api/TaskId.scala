package org.scastie.api

import io.circe._
import io.circe.generic.semiauto._

object TaskId {
  implicit val taskIdEncoder: Encoder[TaskId] = deriveEncoder[TaskId]
  implicit val taskIdDecoder: Decoder[TaskId] = deriveDecoder[TaskId]
}

case class TaskId(snippetId: SnippetId)
