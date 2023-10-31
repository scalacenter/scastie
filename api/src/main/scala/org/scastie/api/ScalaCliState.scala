package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait ScalaCliState extends ServerState
object ScalaCliState {
  case object Unknown extends ScalaCliState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends ScalaCliState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit val scliStateEncoder: Encoder[ScalaCliState] = deriveEncoder[ScalaCliState]
  implicit val scliStateDecoder: Decoder[ScalaCliState] = deriveDecoder[ScalaCliState]

}
