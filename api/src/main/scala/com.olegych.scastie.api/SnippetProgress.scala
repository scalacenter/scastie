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

// note: ProgressActor.Message alias to this
trait ProgressMessage

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
) extends ProgressMessage {
  def isFailure: Boolean = isTimeout || isSbtError || runtimeError.nonEmpty || compilationInfos.exists(_.severity == Error)

  override def toString: String = Json.toJsObject(this).toString()

  def logMsg: String = Json.toJsObject(
    copy(
      scalaJsContent = this.scalaJsContent.map(_ => "..."),
      scalaJsSourceMapContent = this.scalaJsSourceMapContent.map(_ => "...")
    )
  ).toString()
}
