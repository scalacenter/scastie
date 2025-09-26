package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait ProcessOutputType
object ProcessOutputType {
  case object StdOut extends ProcessOutputType
  case object StdErr extends ProcessOutputType

  implicit val processOutputTypeEncoder: Encoder[ProcessOutputType] = deriveEncoder[ProcessOutputType]
  implicit val processOutputTypeDecoder: Decoder[ProcessOutputType] = deriveDecoder[ProcessOutputType]

}

object ProcessOutput {
  implicit val processOutputEncoder: Encoder[ProcessOutput] = deriveEncoder[ProcessOutput]
  implicit val processOutputDecoder: Decoder[ProcessOutput] = deriveDecoder[ProcessOutput]
}

case class ProcessOutput(line: String, tpe: ProcessOutputType, id: Option[Long])
