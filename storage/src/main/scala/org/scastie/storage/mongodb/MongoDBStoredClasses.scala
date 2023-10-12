package org.scastie.storage

import org.scastie.api._

import io.circe._
import io.circe.generic.semiauto._

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
  implicit val shortMongoSnippetEncoder: Encoder[ShortMongoSnippet] = deriveEncoder[ShortMongoSnippet]
  implicit val shortMongoSnippetDecoder: Decoder[ShortMongoSnippet] = deriveDecoder[ShortMongoSnippet]
}

case class PolicyAcceptance(user: String, acceptedPrivacyPolicy: Boolean = false)

object PolicyAcceptance {
  implicit val policyAcceptanceEncoder: Encoder[PolicyAcceptance] = deriveEncoder[PolicyAcceptance]
  implicit val policyAcceptanceDecoder: Decoder[PolicyAcceptance] = deriveDecoder[PolicyAcceptance]
}

case class MongoSnippet(
    simpleSnippetId: String,
    user: Option[String],
    snippetId: SnippetId,
    oldId: Long,
    inputs: BaseInputs,
    progresses: List[SnippetProgress],
    scalaJsContent: String,
    scalaJsSourceMapContent: String,
    time: Long
) extends BaseMongoSnippet {
  def toFetchResult: FetchResult = FetchResult.create(inputs, progresses)
}

object MongoSnippet {
  implicit val mongoSnippetEncoder: Encoder[MongoSnippet] = deriveEncoder[MongoSnippet]
  implicit val mongoSnippetDecoder: Decoder[MongoSnippet] = deriveDecoder[MongoSnippet]

}
