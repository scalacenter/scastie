package com.olegych.scastie.client

import play.api.libs.json._

object ConsoleState {
  implicit val formatConsoleState: OFormat[ConsoleState] =
    Json.format[ConsoleState]

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
