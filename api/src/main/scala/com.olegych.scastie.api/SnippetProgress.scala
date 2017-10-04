package com.olegych.scastie.api

import play.api.libs.json._

object SnippetProgress {
  def default: SnippetProgress =
    SnippetProgress(
      snippetId = None,
      userOutput = None,
      sbtOutput = None,
      compilationInfos = Nil,
      instrumentations = Nil,
      runtimeError = None,
      scalaJsContent = None,
      scalaJsSourceMapContent = None,
      isDone = true,
      isTimeout = false,
      isSbtError = false,
      isForcedProgramMode = false
    )

  implicit val formatSnippetProgress: OFormat[SnippetProgress] =
    Json.format[SnippetProgress]
}

case class SnippetProgress(
    snippetId: Option[SnippetId],
    userOutput: Option[ProcessOutput],
    sbtOutput: Option[ProcessOutput],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    scalaJsContent: Option[String],
    scalaJsSourceMapContent: Option[String],
    isDone: Boolean,
    isTimeout: Boolean,
    isSbtError: Boolean,
    isForcedProgramMode: Boolean
)
