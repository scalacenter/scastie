package com.olegych.scastie.client

import io.circe._
import io.circe.generic.semiauto._

sealed trait View
object View {
  case object Editor extends View
  case object BuildSettings extends View
  case object CodeSnippets extends View
  case object Status extends View

  implicit val viewEncoder: Encoder[View] = deriveEncoder[View]
  implicit val viewDecoder: Decoder[View] = deriveDecoder[View]

}
