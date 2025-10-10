package org.scastie.api

import org.scastie.runtime.api._

import io.circe._
import io.circe.generic.semiauto._

object RuntimeCodecs {
  implicit val errorEncoder: Encoder[ScalaJsResult] = deriveEncoder[ScalaJsResult]
  implicit val errorDecoder: Decoder[ScalaJsResult] = deriveDecoder[ScalaJsResult]

  implicit val runtimeErrorEncoder: Encoder[RuntimeError] = deriveEncoder[RuntimeError]
  implicit val runtimeErrorDecoder: Decoder[RuntimeError] = deriveDecoder[RuntimeError]

  implicit val runtimeErrorWrapEncoder: Encoder[RuntimeErrorWrap] = deriveEncoder[RuntimeErrorWrap]
  implicit val runtimeErrorWrapDecoder: Decoder[RuntimeErrorWrap] = deriveDecoder[RuntimeErrorWrap]

  implicit val renderEncoder: Encoder[Render] = deriveEncoder[Render]
  implicit val renderDecoder: Decoder[Render] = deriveDecoder[Render]

  implicit val instrumentationEncoder: Encoder[Instrumentation] = deriveEncoder[Instrumentation]
  implicit val instrumentationDecoder: Decoder[Instrumentation] = deriveDecoder[Instrumentation]

  implicit val positionEncoder: Encoder[Position] = deriveEncoder[Position]
  implicit val positionDecoder: Decoder[Position] = deriveDecoder[Position]

}
