package com.olegych.scastie.client

object ConsoleStateHelper {
  def default = ConsoleState(
    consoleIsOpen = false,
    consoleHasUserOutput = false,
    userOpenedConsole = true
  )
}