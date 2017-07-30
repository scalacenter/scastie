package com.olegych.scastie.api

import com.olegych.scastie.proto.{TaskId, SnippetId, UUID}

object EnsimeTaskId {
  def create: TaskId.Ensime = {
    val uuid = java.util.UUID.randomUUID()
    TaskId.Ensime(
      uuid = UUID(value = uuid.toString)
    )
  }
}