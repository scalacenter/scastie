package org.scastie.api

import io.circe.generic.semiauto._
import io.circe._

sealed trait SbtState extends ServerState
object SbtState {
  case object Unknown extends SbtState {
    override def toString: String = "Unknown"
    def isReady: Boolean = true
  }

  case object Disconnected extends SbtState {
    override def toString: String = "Disconnected"
    def isReady: Boolean = false
  }

  implicit val sbtStateEncoder: Encoder[SbtState] = deriveEncoder[SbtState]
  implicit val sbtStateDecoder: Decoder[SbtState] = deriveDecoder[SbtState]
}
