package org.scastie.api

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

/**
  * Stable, minimal API models for triggering a compilation run from 3rd party websites.
  *
  * This API is intentionally small and versioned via the HTTP path (e.g. /api/v1/compile)
  * so that Scastie's internal models (like BaseInputs) can evolve independently.
  */

final case class CompilationRequestV1(
    code: String
)

object CompilationRequestV1 {
  implicit val compilationRequestV1Encoder: Encoder[CompilationRequestV1] = deriveEncoder[CompilationRequestV1]
  implicit val compilationRequestV1Decoder: Decoder[CompilationRequestV1] = deriveDecoder[CompilationRequestV1]
}

final case class CompilationResponseV1(
    snippetId: SnippetId,
    url: String
)

object CompilationResponseV1 {
  implicit val compilationResponseV1Encoder: Encoder[CompilationResponseV1] = deriveEncoder[CompilationResponseV1]
  implicit val compilationResponseV1Decoder: Decoder[CompilationResponseV1] = deriveDecoder[CompilationResponseV1]
}


