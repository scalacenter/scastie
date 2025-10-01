package org.scastie.api

import io.circe._
import io.circe.generic.semiauto._

sealed trait ServerState {
  def isReady: Boolean
}

object ServerState {

  case object Unknown extends ServerState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends ServerState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit val sbtStateEncoder: Encoder[ServerState] = deriveEncoder[ServerState]
  implicit val sbtStateDecoder: Decoder[ServerState] = deriveDecoder[ServerState]
}
