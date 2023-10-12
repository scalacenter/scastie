package scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait ScliState extends ServerState
object ScliState {
  case object Unknown extends ScliState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends ScliState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit val scliStateEncoder: Encoder[ScliState] = deriveEncoder[ScliState]
  implicit val scliStateDecoder: Decoder[ScliState] = deriveDecoder[ScliState]

}
