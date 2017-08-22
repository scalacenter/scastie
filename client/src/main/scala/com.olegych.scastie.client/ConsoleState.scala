package com.olegych.scastie.client

import play.api.libs.json._

object ConsoleState {
  implicit val formatConsoleState: play.api.libs.json.OFormat[com.olegych.scastie.client.ConsoleState] = Json.format[ConsoleState]

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
