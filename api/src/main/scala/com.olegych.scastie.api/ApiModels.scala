package com.olegych.scastie
package api

import java.io.{PrintWriter, StringWriter}

case class SnippetUserPart(login: String, update: Option[Int])
case class SnippetId(base64UUID: String, user: Option[SnippetUserPart])

case class SnippetSummary(snippetId: SnippetId, summary: String)

case class FormatRequest(code: String, worksheetMode: Boolean)
case class FormatResponse(formattedCode: Either[String, String])

case class FetchResult(inputs: Inputs, progresses: List[SnippetProgress])

case class ScalaDependency(
  groupId: String,
  artifact: String,
  target: ScalaTarget,
  version: String
)

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

object SnippetProgress {
  def default(snippetId: SnippetId) = 
    SnippetProgress(
      snippetId = snippetId,
      userOutput = None,
      sbtOutput = None,
      compilationInfos = Nil,
      instrumentations = Nil,
      runtimeError = None,
      scalaJsContent = None,
      done = true,
      timeout = false,
      forcedProgramMode = false
    )  
}

case class SnippetProgress(
    snippetId: SnippetId,
    userOutput: Option[String],
    sbtOutput: Option[String],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    scalaJsContent: Option[String],
    done: Boolean,
    timeout: Boolean,
    forcedProgramMode: Boolean
)

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

case class Problem(
  severity: Severity,
  line: Option[Int],
  message: String
)

case class RuntimeError(
  message: String,
  line: Option[Int],
  fullStack: String
)

object RuntimeError {
  def fromTrowable(t: Throwable): Option[RuntimeError] ={
    def search(e: Throwable) = {
      e.getStackTrace
        .find(trace =>
          trace.getFileName == "main.scala" && trace.getLineNumber != -1)
        .map(v ⇒ (e, Some(v.getLineNumber)))
    }
    def loop(e: Throwable): Option[(Throwable, Option[Int])] = {
      val s = search(e)
      if (s.isEmpty)
        if (e.getCause != null) loop(e.getCause)
        else Some((e, None))
      else s
    }

    loop(t).map { case (err, line) ⇒
      val errors = new StringWriter()
      t.printStackTrace(new PrintWriter(errors))
      val fullStack = errors.toString()

      RuntimeError(err.toString, line, fullStack)
    }
  }
}

case class Instrumentation(
  position: Position,
  render: Render
)

sealed trait Render
case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin = copy(a = a.stripMargin)
  def fold = copy(folded = true)
}

case class User(login: String, name: Option[String], avatar_url: String)
