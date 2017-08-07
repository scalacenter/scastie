package com.olegych.scastie
package api

import play.api.libs.json._

object SnippetProgress {
  def default =
    SnippetProgress(
      snippetId = None,
      userOutput = None,
      sbtOutput = None,
      compilationInfos = Nil,
      instrumentations = Nil,
      runtimeError = None,
      scalaJsContent = None,
      scalaJsSourceMapContent = None,
      done = true,
      timeout = false,
      sbtError = false,
      forcedProgramMode = false
    )

  implicit val formatSnippetProgress = Json.format[SnippetProgress]
}

case class SnippetProgress(
    snippetId: Option[SnippetId],
    userOutput: Option[String],
    sbtOutput: Option[String],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    scalaJsContent: Option[String] = None,
    scalaJsSourceMapContent: Option[String] = None,
    done: Boolean,
    timeout: Boolean,
    sbtError: Boolean = false,
    forcedProgramMode: Boolean
)
