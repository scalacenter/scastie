package com.olegych.scastie

object ConsoleState {
  def default = ConsoleState(
    consoleIsOpen = false,
    consoleHasUserOutput = false
  )
}

case class ConsoleState(consoleIsOpen: Boolean, consoleHasUserOutput: Boolean)
