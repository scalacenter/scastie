package com.olegych.scastie
package api

case class SnippetUserPart(login: String, update: Option[Int])
case class SnippetId(base64UUID: String, user: Option[SnippetUserPart])

case class User(login: String, name: Option[String], avatar_url: String)

case class SnippetSummary(snippetId: SnippetId, summary: String)

case class FormatRequest(code: String, worksheetMode: Boolean)
case class FormatResponse(formattedCode: Either[String, String])

case class FetchResult(inputs: Inputs, progresses: List[SnippetProgress])

case class FetchScalaJs(snippetId: SnippetId)
case class FetchResultScalaJs(content: String)

case class FetchScalaJsSourceMap(snippetId: SnippetId)
case class FetchResultScalaJsSourceMap(content: String)

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

// Keep websocket connection
case class KeepAlive(msg: String = "") extends AnyVal
