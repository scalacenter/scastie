package com.olegych.scastie.client

import play.api.libs.json._

object ConsoleState {
  implicit val formatConsoleState: OFormat[ConsoleState] =
    Json.format[ConsoleState]

  def default = ConsoleState(
    consoleIsOpen = false,
    consoleHasUserOutput = false,
    userOpenedConsole = true
  )
}

case class ConsoleState(
    consoleIsOpen: Boolean,
    consoleHasUserOutput: Boolean,
    userOpenedConsole: Boolean = false
)
