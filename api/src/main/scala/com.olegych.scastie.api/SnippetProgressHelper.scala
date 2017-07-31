package com.olegych.scastie.api

import com.olegych.scastie.proto.SnippetProgress

object SnippetProgressHelper {
  def default: SnippetProgress = {
    SnippetProgress(
      snippetId = None,
      userOutput = None,
      sbtOutput = None,
      compilationInfos = Nil,
      instrumentations = Set(),
      runtimeError = None,
      scalaJsContent = None,
      scalaJsSourceMapContent = None,
      done = true,
      timeout = false,
      sbtError = false,
      forcedProgramMode = false
    )
  }
}