package com.olegych.scastie.storage

import play.api.libs.json.OFormat
import play.api.libs.json.Json
import com.olegych.scastie.api._

sealed trait BaseMongoSnippet {
  def snippetId: SnippetId
  def inputs: BaseInputs
  def time: Long
}

case class ShortMongoSnippet(
    snippetId: SnippetId,
    inputs: ShortInputs,
    time: Long
) extends BaseMongoSnippet

object ShortMongoSnippet {
  implicit val formatShortMongoSnippet: OFormat[ShortMongoSnippet] = Json.format[ShortMongoSnippet]
}

case class PolicyAcceptance(user: String, acceptedPrivacyPolicy: Boolean = false)

object PolicyAcceptance {
  implicit val formatPolicyAcceptance: OFormat[PolicyAcceptance] = Json.format[PolicyAcceptance]
}

case class MongoSnippet(
    simpleSnippetId: String,
    user: Option[String],
    snippetId: SnippetId,
    oldId: Long,
    inputs: Inputs,
    progresses: List[SnippetProgress],
    scalaJsContent: String,
    scalaJsSourceMapContent: String,
    time: Long
) extends BaseMongoSnippet {
  def toFetchResult: FetchResult = FetchResult.create(inputs, progresses)
}

object MongoSnippet {
  implicit val formatMongoSnippet: OFormat[MongoSnippet] = Json.format[MongoSnippet]
}
