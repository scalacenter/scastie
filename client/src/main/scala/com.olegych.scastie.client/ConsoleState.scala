package com.olegych.scastie.client

object ConsoleState {
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
