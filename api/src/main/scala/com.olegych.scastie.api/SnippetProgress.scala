package com.olegych.scastie.api

import play.api.libs.json._

object SnippetProgress {
  def default: SnippetProgress =
    SnippetProgress(
      ts = None,
      id = None,
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

  implicit val formatSnippetProgress: OFormat[SnippetProgress] = Json.format[SnippetProgress]
}

case class SnippetProgress(
    ts: Option[Long],
    id: Option[Long],
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
) {
  def isFailure: Boolean = isTimeout || isSbtError || runtimeError.nonEmpty || compilationInfos.exists(_.severity == Error)

  override def toString: String = Json.toJsObject(this).toString()
}
