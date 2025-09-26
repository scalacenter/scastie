package org.scastie.client

import io.circe._
import io.circe.generic.semiauto._

object ConsoleState {
  implicit val consoleStateEncoder: Encoder[ConsoleState] = deriveEncoder[ConsoleState]
  implicit val consoleStateDecoder: Decoder[ConsoleState] = deriveDecoder[ConsoleState]

  def default: ConsoleState = ConsoleState(
    consoleIsOpen = false,
    consoleHasUserOutput = false,
    userOpenedConsole = false
  )
}

case class ConsoleState(
    consoleIsOpen: Boolean,
    consoleHasUserOutput: Boolean,
    userOpenedConsole: Boolean
)
