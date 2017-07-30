package com.olegych.scastie.api

import com.olegych.scastie.proto.{TaskId, SnippetId}

object SbtRunTaskId {
  def apply(snippetId: SnippetId): TaskId = {
    TaskId(
      value = TaskId.Value.WrapSbtRun(
        TaskId.SbtRun(
          snippetId = snippetId
        )
      )
    )
  }
}